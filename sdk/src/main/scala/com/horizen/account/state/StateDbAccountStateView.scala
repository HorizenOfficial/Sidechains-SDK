package com.horizen.account.state

import com.google.common.primitives.Bytes
import com.horizen.SidechainTypes
import com.horizen.account.proposition.AddressProposition
import com.horizen.account.receipt.EthereumConsensusDataReceipt
import com.horizen.account.receipt.EthereumConsensusDataReceipt.ReceiptStatus
import com.horizen.account.state.ForgerStakeMsgProcessor.AddNewStakeCmd
import com.horizen.account.transaction.EthereumTransaction
import com.horizen.account.utils.WellKnownAddresses.{FORGER_STAKE_SMART_CONTRACT_ADDRESS_BYTES, NULL_ADDRESS_BYTES}
import com.horizen.account.utils.{MainchainTxCrosschainOutputAddressUtil, ZenWeiConverter}
import com.horizen.block.{MainchainBlockReferenceData, MainchainTxForwardTransferCrosschainOutput, MainchainTxSidechainCreationCrosschainOutput}
import com.horizen.certificatesubmitter.keys.{CertifiersKeys, KeyRotationProof, KeyRotationProofTypes}
import com.horizen.consensus.ForgingStakeInfo
import com.horizen.evm.{ResourceHandle, StateDB, StateStorageStrategy}
import com.horizen.evm.interop.{EvmLog, ProofAccountResult}
import com.horizen.proposition.{PublicKey25519Proposition, VrfPublicKey}
import com.horizen.transaction.mainchain.{ForwardTransfer, SidechainCreation}
import com.horizen.utils.BytesUtils
import scorex.util.ScorexLogging
import java.math.BigInteger
import java.util.Optional

import com.horizen.account.sc2sc.{AccountCrossChainMessage, CrossChainMessageProvider}
import com.horizen.sc2sc.{CrossChainMessageHash, CrossChainMessage}

import scala.collection.JavaConverters.asScalaBufferConverter
import scala.util.Try

class StateDbAccountStateView(
                               stateDb: StateDB,
                               messageProcessors: Seq[MessageProcessor])
  extends BaseAccountStateView
    with AutoCloseable
    with ScorexLogging {

  lazy val withdrawalReqProvider: WithdrawalRequestProvider = messageProcessors.find(_.isInstanceOf[WithdrawalRequestProvider]).get.asInstanceOf[WithdrawalRequestProvider]
  lazy val forgerStakesProvider: ForgerStakesProvider = messageProcessors.find(_.isInstanceOf[ForgerStakesProvider]).get.asInstanceOf[ForgerStakesProvider]
  lazy val certificateKeysProvider: CertificateKeysProvider = messageProcessors.find(_.isInstanceOf[CertificateKeysProvider]).get.asInstanceOf[CertificateKeysProvider]
  lazy val crossChainMessageProviders: Seq[CrossChainMessageProvider] = messageProcessors.filter(_.isInstanceOf[CrossChainMessageProvider]).map(_.asInstanceOf[CrossChainMessageProvider])

  override def keyRotationProof(withdrawalEpoch: Int, indexOfSigner: Int, keyType: Int): Option[KeyRotationProof] = {
    certificateKeysProvider.getKeyRotationProof(withdrawalEpoch, indexOfSigner, KeyRotationProofTypes(keyType), this)
  }

  override def certifiersKeys(withdrawalEpoch: Int): Option[CertifiersKeys] = {
    Some(certificateKeysProvider.getCertifiersKeys(withdrawalEpoch, this))
  }

  override def getWithdrawalRequests(withdrawalEpoch: Int): Seq[WithdrawalRequest] =
    withdrawalReqProvider.getListOfWithdrawalReqRecords(withdrawalEpoch, this)

  override def getForgerStakeData(stakeId: String): Option[ForgerStakeData] =
    forgerStakesProvider.findStakeData(this, BytesUtils.fromHexString(stakeId))

  override def isForgingOpen: Boolean =
    forgerStakesProvider.isForgerListOpen(this)

  override def getListOfForgersStakes: Seq[AccountForgingStakeInfo] =
    forgerStakesProvider.getListOfForgersStakes(this)

  override def getAllowedForgerList: Seq[Int] =
    forgerStakesProvider.getAllowedForgerListIndexes(this)

  override def getCrossChainMessages(withdrawalEpoch: Int): Seq[CrossChainMessage] =
    crossChainMessageProviders.flatMap(_.getCrossChainMesssages(withdrawalEpoch, this))

  override def getCrossChainMessageHashEpoch(msgHash: CrossChainMessageHash): Option[Int] = {
    val providerWithMessage : Option[CrossChainMessageProvider] =  crossChainMessageProviders.find(_.getCrossChainMessageHashEpoch(msgHash, this).nonEmpty)
    providerWithMessage  match {
      case Some(x) => x.getCrossChainMessageHashEpoch(msgHash, this)
      case None => Option.empty
    }
  }

  def applyMainchainBlockReferenceData(refData: MainchainBlockReferenceData): Unit = {
    refData.sidechainRelatedAggregatedTransaction.foreach(aggTx => {
      aggTx.mc2scTransactionsOutputs().asScala.map {
        case sc: SidechainCreation =>
          // While processing sidechain creation output:
          // 1. extract first forger stake info: block sign public key, vrf public key, owner address, stake amount
          // 2. store the stake info record in the forging fake smart contract storage
          val scOut: MainchainTxSidechainCreationCrosschainOutput = sc.getScCrOutput

          val stakedAmount = ZenWeiConverter.convertZenniesToWei(scOut.amount)

          val ownerAddressProposition = new AddressProposition(
            MainchainTxCrosschainOutputAddressUtil.getAccountAddress(scOut.address))

          // customData = vrf key | blockSignerKey
          val vrfPublicKey = new VrfPublicKey(scOut.customCreationData.take(VrfPublicKey.KEY_LENGTH))
          val blockSignerProposition = new PublicKey25519Proposition(scOut.customCreationData.slice(VrfPublicKey.KEY_LENGTH, VrfPublicKey.KEY_LENGTH + PublicKey25519Proposition.KEY_LENGTH))

          val cmdInput = AddNewStakeCmdInput(
            ForgerPublicKeys(blockSignerProposition, vrfPublicKey),
            ownerAddressProposition
          )
          val data = Bytes.concat(BytesUtils.fromHexString(AddNewStakeCmd), cmdInput.encode())

          val message = new Message(
            Optional.of(ownerAddressProposition),
            Optional.of(new AddressProposition(FORGER_STAKE_SMART_CONTRACT_ADDRESS_BYTES)),
            BigInteger.ZERO, // gasPrice
            BigInteger.ZERO, // gasFeeCap
            BigInteger.ZERO, // gasTipCap
            BigInteger.ZERO, // gasLimit
            stakedAmount,
            BigInteger.ONE.negate(), // a negative nonce value will rule out collision with real transactions
            data,
            false)

          val returnData = forgerStakesProvider.addScCreationForgerStake(message, this)
          log.debug(s"sc creation forging stake added with stakeid: ${BytesUtils.toHexString(returnData)}")

        case ft: ForwardTransfer =>
          val ftOut: MainchainTxForwardTransferCrosschainOutput = ft.getFtOutput

          // we trust the MC that this is a valid amount
          val value = ZenWeiConverter.convertZenniesToWei(ftOut.amount)

          val recipientProposition = new AddressProposition(
            MainchainTxCrosschainOutputAddressUtil.getAccountAddress(ftOut.propositionBytes))

          if (isEoaAccount(recipientProposition.address())) {
            // stateDb will implicitly create account if not existing yet
            addBalance(recipientProposition.address(), value)
            log.debug(s"added FT amount = $value to address=$recipientProposition")
          } else {
            log.warn(s"ignored FT to non-EOA account, amount = $value to address=$recipientProposition (the amount was burned by sending balance to ${BytesUtils.toHexString(NULL_ADDRESS_BYTES)} address)")
            addBalance(NULL_ADDRESS_BYTES, value)
            // TODO: we should return the amount back to mcReturnAddress instead of just burning it
          }
      }
    })
  }

  def getOrderedForgingStakesInfoSeq: Seq[ForgingStakeInfo] = {
    // get forger stakes list view (scala lazy collection)
    getListOfForgersStakes.view

      // group delegation stakes by blockSignPublicKey/vrfPublicKey pairs
      .groupBy(stake => (stake.forgerStakeData.forgerPublicKeys.blockSignPublicKey,
        stake.forgerStakeData.forgerPublicKeys.vrfPublicKey))

      // create a seq of forging stake info for every group entry summing all the delegation amounts.
      // Note: ForgingStakeInfo amount is a long and contains a Zennies amount converted from a BigInteger wei amount
      //       That is safe since the stakedAmount is checked in creation phase to be an exact zennies amount
      .map { case ((blockSignKey, vrfKey), stakes) =>
        ForgingStakeInfo(
          blockSignKey,
          vrfKey,
          stakes.map(
            stake =>
              ZenWeiConverter.convertWeiToZennies(stake.forgerStakeData.stakedAmount)).sum) }
      .toSeq

      // sort the resulting sequence by decreasing stake amount
      .sorted(Ordering[ForgingStakeInfo].reverse)
  }

  def setupTxContext(txHash: Array[Byte], idx: Integer): Unit = {
    // set context for the created events/logs assignment
    stateDb.setTxContext(txHash, idx)
  }

  @throws(classOf[InvalidMessageException])
  @throws(classOf[ExecutionFailedException])
  def applyMessage(msg: Message, blockGasPool: GasPool, blockContext: BlockContext): Array[Byte] = {
    new StateTransition(this, messageProcessors, blockGasPool, blockContext).transition(msg)
  }

  /**
   * Possible outcomes:
   *  - tx applied succesfully => Receipt with status success
   *  - tx execution failed => Receipt with status failed
   *    - if any ExecutionFailedException was thrown, including but not limited to:
   *    - OutOfGasException (not intrinsic gas, see below!)
   *    - EvmException (EVM reverted) / fake contract exception
   *  - tx could not be applied => throws an exception (this will lead to an invalid block)
   *    - any of the preChecks fail
   *    - not enough gas for intrinsic gas
   *    - block gas limit reached
   */
  def applyTransaction(
                        tx: SidechainTypes#SCAT,
                        txIndex: Int,
                        blockGasPool: GasPool,
                        blockContext: BlockContext,
                        finalizeChanges: Boolean = true
                      ): Try[EthereumConsensusDataReceipt] = Try {
    if (!tx.isInstanceOf[EthereumTransaction])
      throw new IllegalArgumentException(s"Unsupported transaction type ${tx.getClass.getName}")

    val ethTx = tx.asInstanceOf[EthereumTransaction]
    val txHash = BytesUtils.fromHexString(ethTx.id)
    val msg = ethTx.asMessage(blockContext.baseFee)

    // Tx context for stateDB, to know where to keep EvmLogs
    setupTxContext(txHash, txIndex)

    log.debug(s"applying msg: used pool gas ${blockGasPool.getUsedGas}")
    // apply message to state
    val status = try {
      applyMessage(msg, blockGasPool, blockContext)
      ReceiptStatus.SUCCESSFUL
    } catch {
      // any other exception will bubble up and invalidate the block
      case err: ExecutionFailedException =>
        log.error(s"applying message failed, tx.id=${ethTx.id}", err)
        ReceiptStatus.FAILED
    } finally {
      // finalize pending changes, clear the journal and reset refund counter
      if (finalizeChanges)
        stateDb.finalizeChanges()
    }
    val consensusDataReceipt = new EthereumConsensusDataReceipt(
      ethTx.version(), status.id, blockGasPool.getUsedGas, getLogs(txHash))
    log.debug(s"Returning consensus data receipt: ${consensusDataReceipt.toString()}")
    log.debug(s"applied msg: used pool gas ${blockGasPool.getUsedGas}")

    consensusDataReceipt
  }

  override def isEoaAccount(address: Array[Byte]): Boolean =
    stateDb.isEoaAccount(address)

  override def isSmartContractAccount(address: Array[Byte]): Boolean =
    stateDb.isSmartContractAccount(address)

  override def accountExists(address: Array[Byte]): Boolean =
    !stateDb.isEmpty(address)

  // account modifiers:
  override def addAccount(address: Array[Byte], code: Array[Byte]): Unit =
    stateDb.setCode(address, code)

  override def increaseNonce(address: Array[Byte]): Unit =
    stateDb.setNonce(address, getNonce(address).add(BigInteger.ONE))

  @throws(classOf[ExecutionFailedException])
  override def addBalance(address: Array[Byte], amount: BigInteger): Unit = {
    amount.signum() match {
      case x if x == 0 => // amount is zero
      case x if x < 0 =>
        throw new ExecutionFailedException("cannot add negative amount to balance")
      case _ =>
        stateDb.addBalance(address, amount)
    }
  }

  @throws(classOf[ExecutionFailedException])
  override def subBalance(address: Array[Byte], amount: BigInteger): Unit = {
    // stateDb lib does not do any sanity check, and negative balances might arise (and java/go json IF does not correctly handle it)
    amount.signum() match {
      case x if x == 0 => // amount is zero, do nothing
      case x if x < 0 =>
        throw new ExecutionFailedException("cannot subtract negative amount from balance")
      case _ =>
        // The check on the address balance to be sufficient to pay the amount at this point has already been
        // done by the state while validating the origin tx
        stateDb.subBalance(address, amount)
    }
  }

  override def getAccountStorage(address: Array[Byte], key: Array[Byte]): Array[Byte] =
    stateDb.getStorage(address, key, StateStorageStrategy.RAW)

  override def getAccountStorageBytes(address: Array[Byte], key: Array[Byte]): Array[Byte] =
    stateDb.getStorage(address, key, StateStorageStrategy.CHUNKED)

  override def updateAccountStorage(address: Array[Byte], key: Array[Byte], value: Array[Byte]): Unit =
    stateDb.setStorage(address, key, value, StateStorageStrategy.RAW)

  override def updateAccountStorageBytes(address: Array[Byte], key: Array[Byte], value: Array[Byte]): Unit =
    stateDb.setStorage(address, key, value, StateStorageStrategy.CHUNKED)

  override def removeAccountStorage(address: Array[Byte], key: Array[Byte]): Unit =
    stateDb.removeStorage(address, key, StateStorageStrategy.RAW)

  override def removeAccountStorageBytes(address: Array[Byte], key: Array[Byte]): Unit =
    stateDb.removeStorage(address, key, StateStorageStrategy.CHUNKED)

  def getProof(address: Array[Byte], keys: Array[Array[Byte]]): ProofAccountResult =
    stateDb.getProof(address, keys)

  // account specific getters
  override def getNonce(address: Array[Byte]): BigInteger = stateDb.getNonce(address)

  override def getBalance(address: Array[Byte]): BigInteger = stateDb.getBalance(address)

  override def getCodeHash(address: Array[Byte]): Array[Byte] = stateDb.getCodeHash(address)

  override def getCode(address: Array[Byte]): Array[Byte] = stateDb.getCode(address)

  override def getLogs(txHash: Array[Byte]): Array[EvmLog] = stateDb.getLogs(txHash)

  override def addLog(evmLog: EvmLog): Unit = stateDb.addLog(evmLog)

  // when a method is called on a closed handle, LibEvm throws an exception
  override def close(): Unit = stateDb.close()

  override def getStateDbHandle: ResourceHandle = stateDb

  override def getIntermediateRoot: Array[Byte] = stateDb.getIntermediateRoot

  def getRefund: BigInteger = stateDb.getRefund

  def snapshot: Int = stateDb.snapshot()

  def finalizeChanges(): Unit =  stateDb.finalizeChanges()

  def revertToSnapshot(revisionId: Int): Unit = stateDb.revertToSnapshot(revisionId)
}





