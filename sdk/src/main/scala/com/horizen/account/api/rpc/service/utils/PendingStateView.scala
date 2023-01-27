package com.horizen.account.api.rpc.service.utils

import com.horizen.account.block.AccountBlock
import com.horizen.account.history.AccountHistory
import com.horizen.account.mempool.AccountMemoryPool
import com.horizen.account.receipt.EthereumReceipt
import com.horizen.account.state._
import com.horizen.account.transaction.EthereumTransaction
import com.horizen.account.utils.Account.generateContractAddress
import com.horizen.account.utils._
import com.horizen.account.wallet.AccountWallet
import com.horizen.block._
import com.horizen.utils.{BytesUtils, ClosableResourceHandler, TimeToEpochUtils, WithdrawalEpochUtils}
import scorex.util.ScorexLogging
import sparkz.core.NodeViewHolder.CurrentView
import sparkz.core.idToBytes

import java.math.BigInteger
import java.util
import scala.collection.mutable.ListBuffer
import scala.util.{Failure, Success}

case class PendingStateView(
    nodeView: CurrentView[AccountHistory, AccountState, AccountWallet, AccountMemoryPool],
    mod: AccountBlock
) extends ScorexLogging with ClosableResourceHandler {
  def getPendingStateView: AccountStateView = {
    val networkParams = nodeView.state.params
    val stateView = nodeView.state.getView
    if (stateView.hasCeased) {
      val errMsg = s"Can't apply Block ${mod.id}, because the sidechain has ceased."
      log.error(errMsg)
      throw new IllegalStateException(errMsg)
    }

    // Check Txs semantic validity first
    for (tx <- mod.sidechainTransactions)
      tx.semanticValidity()

    // TODO: keep McBlockRef validation in a view style, so in the applyMainchainBlockReferenceData method
    // Validate top quality certificate in the end of the submission window:
    // Reject block if it refers to the chain that conflicts with the top quality certificate content
    // Mark sidechain as ceased in case there is no certificate appeared within the submission window.
    val currentWithdrawalEpochInfo = stateView.getWithdrawalEpochInfo
    val modWithdrawalEpochInfo =
      WithdrawalEpochUtils.getWithdrawalEpochInfo(mod, currentWithdrawalEpochInfo, networkParams)

    // Check top quality certificate or notify that sidechain has ceased since we have no certificate in the end of the submission window.
    if (networkParams.isNonCeasing) {
      // For non-ceasing sidechains certificate must be validated just when it has been received.
      // In case of multiple certificates appeared and at least one of them is invalid (conflicts with the current chain)
      // then the whole block is invalid.
      mod.topQualityCertificateOpt.foreach(cert => validateTopQualityCertificate(cert, stateView))
    } else {
      // For ceasing sidechains submission window concept is used.
      // If SC block has reached the certificate submission window end -> check the top quality certificate
      // Note: even if mod contains multiple McBlockRefData entries, we are sure they belongs to the same withdrawal epoch.
      if (
        WithdrawalEpochUtils.hasReachedCertificateSubmissionWindowEnd(mod, currentWithdrawalEpochInfo, networkParams)
      ) {
        val certReferencedEpochNumber = modWithdrawalEpochInfo.epoch - 1

        // Top quality certificate may present in the current SC block or in the previous blocks or can be absent.
        val topQualityCertificateOpt: Option[WithdrawalEpochCertificate] = mod.topQualityCertificateOpt.orElse(
          stateView.getTopQualityCertificate(certReferencedEpochNumber)
        )

        // Check top quality certificate or notify that sidechain has ceased since we have no certificate in the end of the submission window.
        topQualityCertificateOpt match {
          case Some(cert) =>
            validateTopQualityCertificate(cert, stateView)
          case None =>
            log.info(s"In the end of the certificate submission window of epoch ${modWithdrawalEpochInfo.epoch} " +
              s"there are no certificates referenced to the epoch $certReferencedEpochNumber. Sidechain has ceased.")
            stateView.setCeased()
        }
      }
    }

    // Update view with the block info
    stateView.updateWithdrawalEpochInfo(modWithdrawalEpochInfo)

    val consensusEpochNumber = TimeToEpochUtils.timeStampToEpochNumber(networkParams, mod.timestamp)
    stateView.updateConsensusEpochNumber(consensusEpochNumber)

    for (mcBlockRefData <- mod.mainchainBlockReferencesData) {
      stateView.addTopQualityCertificates(mcBlockRefData, mod.id)
      stateView.applyMainchainBlockReferenceData(mcBlockRefData)
    }

    // get also list of receipts, useful for computing the receiptRoot hash
    val receiptList = new ListBuffer[EthereumReceipt]()
    val blockNumber = nodeView.history.getCurrentHeight + 1
    val blockHash = idToBytes(mod.id)

    var cumGasUsed: BigInteger = BigInteger.ZERO
    var cumBaseFee: BigInteger = BigInteger.ZERO // cumulative base-fee, burned in eth, goes to forgers pool
    var cumForgerTips: BigInteger = BigInteger.ZERO // cumulative max-priority-fee, is paid to block forger

    val blockGasPool = new GasPool(mod.header.gasLimit)
    val blockContext =
      new BlockContext(
        mod.header,
        blockNumber,
        consensusEpochNumber,
        modWithdrawalEpochInfo.epoch,
        networkParams.chainId
      )

    for ((tx, txIndex) <- mod.sidechainTransactions.zipWithIndex) {
      stateView.applyTransaction(tx, txIndex, blockGasPool, blockContext) match {
        case Success(consensusDataReceipt) =>
          val txGasUsed = consensusDataReceipt.cumulativeGasUsed.subtract(cumGasUsed)
          // update cumulative gas used so far
          cumGasUsed = consensusDataReceipt.cumulativeGasUsed
          val ethTx = tx.asInstanceOf[EthereumTransaction]

          val txHash = BytesUtils.fromHexString(ethTx.id)

          // The contract address created, if the transaction was a contract creation
          val contractAddress = if (ethTx.getTo.isEmpty) {
            // this w3j util method is equivalent to the createAddress() in geth triggered also by CREATE opcode.
            // Note: geth has also a CREATE2 opcode which may be optionally used in a smart contract solidity implementation
            // in order to deploy another (deeper) smart contract with an address that is pre-determined before deploying it.
            // This does not impact our case since the CREATE2 result would not be part of the receipt.
            Option(generateContractAddress(ethTx.getFrom.address, ethTx.getNonce))
          } else {
            // otherwise nothing
            None
          }

          // get a receipt obj with non consensus data (logs updated too)
          val fullReceipt =
            EthereumReceipt(consensusDataReceipt, txHash, txIndex, blockHash, blockNumber, txGasUsed, contractAddress)

          log.debug(s"Adding to receipt list: ${fullReceipt.toString()}")

          receiptList += fullReceipt

          val baseFeePerGas = blockContext.baseFee
          val (txBaseFeePerGas, txMaxPriorityFeePerGas) = GasUtil.getTxFeesPerGas(ethTx, baseFeePerGas)
          cumBaseFee = cumBaseFee.add(txBaseFeePerGas.multiply(txGasUsed))
          cumForgerTips = cumForgerTips.add(txMaxPriorityFeePerGas.multiply(txGasUsed))

        case Failure(err: GasLimitReached) =>
          log.error("Could not apply tx, block gas limit exceeded")
          throw new IllegalArgumentException("Could not apply tx, block gas limit exceeded", err)

        case Failure(err) =>
          log.error("Could not apply tx", err)
          throw new IllegalArgumentException(err)
      }
    }

    log.debug(s"cumBaseFee=$cumBaseFee, cumForgerTips=$cumForgerTips")

    // The two contributions will go like this:
    // - base -> forgers pool, weighted by number of blocks forged
    // - tip -> block forger
    // Note: store also entries with zero values, which can arise in sc blocks without any tx
    stateView.updateFeePaymentInfo(AccountBlockFeeInfo(cumBaseFee, cumForgerTips, mod.header.forgerAddress))

    // check logs bloom consistency with block header
    mod.verifyLogsBloomConsistency(receiptList)

    // check stateRoot and receiptRoot against block header
    mod.verifyReceiptDataConsistency(receiptList.map(_.consensusDataReceipt))

    val stateRoot = stateView.getIntermediateRoot
    mod.verifyStateRootDataConsistency(stateRoot)

    // eventually, store full receipts in the metaDataStorage indexed by txid
    stateView.updateTransactionReceipts(receiptList)

    // update next base fee
    stateView.updateNextBaseFee(FeeUtils.calculateNextBaseFee(mod))

    stateView
  }

  private def validateTopQualityCertificate(
      topQualityCertificate: WithdrawalEpochCertificate,
      stateView: AccountStateView
  ): Unit = {

    val certReferencedEpochNumber: Int = topQualityCertificate.epochNumber

    // Check that the top quality certificate data is relevant to the SC active chain cert data.
    // There is no need to check endEpochBlockHash, epoch number and Snark proof, because SC trusts MC consensus.
    // Currently we need to check only the consistency of backward transfers and utxoMerkleRoot
    val expectedWithdrawalRequests = stateView.getWithdrawalRequests(certReferencedEpochNumber)

    // Simple size check
    if (topQualityCertificate.backwardTransferOutputs.size != expectedWithdrawalRequests.size) {
      throw new IllegalStateException(
        s"Epoch $certReferencedEpochNumber top quality certificate backward transfers " +
          s"number ${topQualityCertificate.backwardTransferOutputs.size} is different than expected ${expectedWithdrawalRequests.size}. " +
          s"Node's active chain is the fork from MC perspective."
      )
    }

    // Check that BTs are identical for both Cert and State
    topQualityCertificate.backwardTransferOutputs.zip(expectedWithdrawalRequests).foreach {
      case (certOutput, expectedWithdrawalRequest) =>
        if (
          certOutput.amount != expectedWithdrawalRequest.valueInZennies ||
          !util.Arrays.equals(certOutput.pubKeyHash, expectedWithdrawalRequest.proposition.bytes())
        ) {
          throw new IllegalStateException(
            s"Epoch $certReferencedEpochNumber top quality certificate backward transfers " +
              s"data is different than expected. Node's active chain is the fork from MC perspective."
          )
        }
    }
  }
}
