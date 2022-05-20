package com.horizen.account.state

import com.horizen.SidechainTypes
import com.horizen.account.block.SidechainAccountBlock
import com.horizen.block.{MainchainBlockReferenceData, WithdrawalEpochCertificate}
import com.horizen.box.{ForgerBox, WithdrawalRequestBox}
import com.horizen.params.NetworkParams
import com.horizen.state.State
import com.horizen.utils.{BlockFeeInfo, BytesUtils, FeePaymentsUtils, WithdrawalEpochInfo, WithdrawalEpochUtils}
import scorex.core.{VersionTag, idToBytes, idToVersion, versionToBytes}
import scorex.util.ScorexLogging

import java.util
import scala.util.{Failure, Success, Try}

class AccountState(val params: NetworkParams) extends State[SidechainTypes#SCAT, SidechainAccountBlock, AccountStateView, AccountState]
  with AccountStateReader
  with ScorexLogging {

  override type NVCT = AccountState

  // Modifiers:
  override def applyModifier(mod: SidechainAccountBlock): Try[AccountState] = Try {
    require(versionToBytes(version).sameElements(idToBytes(mod.parentId)),
      s"Incorrect state version!: ${mod.parentId} found, " + s"$version expected")

    var stateView: AccountStateView = getView

    if(stateView.hasCeased) {
      throw new IllegalStateException(s"Can't apply Block ${mod.id}, because the sidechain has ceased.")
    }

    // Check Txs semantic validity first
    for(tx <- mod.sidechainTransactions)
      tx.semanticValidity()

    // Validate top quality certificate in the end of the submission window:
    // Reject block if it refers to the chain that conflicts with the top quality certificate content
    // Mark sidechain as ceased in case there is no certificate appeared within the submission window.
    val currentWithdrawalEpochInfo = stateView.getWithdrawalEpochInfo
    val modWithdrawalEpochInfo: WithdrawalEpochInfo = WithdrawalEpochUtils.getWithdrawalEpochInfo(mod, currentWithdrawalEpochInfo, params)

    // If SC block has reached the certificate submission window end -> check the top quality certificate
    // Note: even if mod contains multiple McBlockRefData entries, we are sure they belongs to the same withdrawal epoch.
    if(WithdrawalEpochUtils.hasReachedCertificateSubmissionWindowEnd(mod, currentWithdrawalEpochInfo, params)) {
      val certReferencedEpochNumber = modWithdrawalEpochInfo.epoch - 1

      // Top quality certificate may present in the current SC block or in the previous blocks or can be absent.
      val topQualityCertificateOpt: Option[WithdrawalEpochCertificate] = mod.topQualityCertificateOpt.orElse(
        stateView.certificate(certReferencedEpochNumber))

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

    // Update view with the block info
    stateView.updateWithdrawalEpochInfo(modWithdrawalEpochInfo).get
    stateView.addFeeInfo(mod.feeInfo)

    // If SC block has reached the end of the withdrawal epoch -> fee payments expected to be produced.
    // Verify that Forger assumed the same fees to be paid as the current node does.
    // If SC block is in the middle of the withdrawal epoch -> no fee payments hash expected to be defined.
    val isWithdrawalEpochFinished: Boolean = WithdrawalEpochUtils.isEpochLastIndex(modWithdrawalEpochInfo, params)
    if(isWithdrawalEpochFinished) {
      // Note: that current block fee info is already in the view
      // TODO: get the list of block info and recalculate the root of it
      val feePayments = stateView.getBlockFeePayments(modWithdrawalEpochInfo.epoch)
      val feePaymentsHash: Array[Byte] = new Array[Byte](32)// TODO: analog of FeePaymentsUtils.calculateFeePaymentsHash(feePayments)

      if(!mod.feePaymentsHash.sameElements(feePaymentsHash))
        throw new IllegalArgumentException(s"Block ${mod.id} has feePaymentsHash different to expected one: ${BytesUtils.toHexString(feePaymentsHash)}")
    } else {
      // No fee payments expected
      if(!mod.feePaymentsHash.sameElements(FeePaymentsUtils.DEFAULT_FEE_PAYMENTS_HASH))
        throw new IllegalArgumentException(s"Block ${mod.id} has feePaymentsHash ${BytesUtils.toHexString(mod.feePaymentsHash)} defined when no fee payments expected.")
    }

    for(mcBlockRefData <- mod.mainchainBlockReferencesData) {
      stateView = stateView.applyMainchainBlockReferenceData(mcBlockRefData).get
    }

    for(tx <- mod.sidechainTransactions) {
      stateView = stateView.applyTransaction(tx).get
    }

    stateView.commit(idToVersion(mod.id)).get

    this
  }

  private def validateTopQualityCertificate(topQualityCertificate: WithdrawalEpochCertificate, stateView: AccountStateView): Unit = {
    val certReferencedEpochNumber: Int = topQualityCertificate.epochNumber

    // Check that the top quality certificate data is relevant to the SC active chain cert data.
    // There is no need to check endEpochBlockHash, epoch number and Snark proof, because SC trusts MC consensus.
    // Currently we need to check only the consistency of backward transfers and utxoMerkleRoot
    val expectedWithdrawalRequests = stateView.withdrawalRequests(certReferencedEpochNumber)

    // Simple size check
    if (topQualityCertificate.backwardTransferOutputs.size != expectedWithdrawalRequests.size) {
      throw new IllegalStateException(s"Epoch $certReferencedEpochNumber top quality certificate backward transfers " +
        s"number ${topQualityCertificate.backwardTransferOutputs.size} is different than expected ${expectedWithdrawalRequests.size}. " +
        s"Node's active chain is the fork from MC perspective.")
    }

    // Check that BTs are identical for both Cert and State
    topQualityCertificate.backwardTransferOutputs.zip(expectedWithdrawalRequests).foreach {
      case (certOutput, expectedWithdrawalRequestBox) => {
        if(certOutput.amount != expectedWithdrawalRequestBox.value() ||
          !util.Arrays.equals(certOutput.pubKeyHash, expectedWithdrawalRequestBox.proposition().bytes())) {
          throw new IllegalStateException(s"Epoch $certReferencedEpochNumber top quality certificate backward transfers " +
            s"data is different than expected. Node's active chain is the fork from MC perspective.")
        }
      }
    }

    // TODO: no CSW support expected for the Eth sidechain
    /*if(topQualityCertificate.fieldElementCertificateFields.size != 2)
      throw new IllegalArgumentException(s"Top quality certificate should contain exactly 2 custom fields.")

    utxoMerkleTreeRoot(certReferencedEpochNumber) match {
      case Some(expectedMerkleTreeRoot) =>
        val certUtxoMerkleRoot = CryptoLibProvider.sigProofThresholdCircuitFunctions.reconstructUtxoMerkleTreeRoot(
          topQualityCertificate.fieldElementCertificateFields.head.fieldElementBytes(params.sidechainCreationVersion),
          topQualityCertificate.fieldElementCertificateFields(1).fieldElementBytes(params.sidechainCreationVersion)
        )
        if(!expectedMerkleTreeRoot.sameElements(certUtxoMerkleRoot))
          throw new IllegalStateException(s"Epoch $certReferencedEpochNumber top quality certificate utxo merkle tree root " +
            s"data is different than expected. Node's active chain is the fork from MC perspective.")
      case None =>
        throw new IllegalArgumentException(s"There is no utxo merkle tree root stored for the referenced epoch $certReferencedEpochNumber.")
    }*/
  }

  override def rollbackTo(version: VersionTag): Try[AccountState] = ???

  // versions part
  override def version: VersionTag = ???

  override def maxRollbackDepth: Int = ???

  // View
  override def getView: AccountStateView = ???

  // getters:
  override def withdrawalRequests(withdrawalEpoch: Int): Seq[WithdrawalRequestBox] = ???

  override def certificate(referencedWithdrawalEpoch: Int): Option[WithdrawalEpochCertificate] = ???

  override def certificateTopQuality(referencedWithdrawalEpoch: Int): Long = ???

  override def getWithdrawalEpochInfo: WithdrawalEpochInfo = ???

  override def hasCeased: Boolean = ???

  override def getBlockFeePayments(withdrawalEpochNumber: Int): Seq[BlockFeeInfo] = ???

  // Account specific getters
  override def getAccount(address: Array[Byte]): Account = ???

  override def getBalance(address: Array[Byte]): Long = ???
}



// Types tests:

/*trait FinalAccountStateReader extends AccountStateReader {
  def getSomething: Int = 42
}

class FinalAccountView(stateStorageView: SidechainStateStorageView, stateDb: StateDB) extends AccountStateView[FinalAccountView] with FinalAccountStateReader {
  override def commit(version: VersionTag): Try[Unit] = {
    stateStorageView.commit(version)
    stateDb.commit(stateRoot)
  }
}

class FinalState(stateStorage: SidechainStateStorage) extends AccountState[FinalAccountView, FinalState] with FinalAccountStateReader {
  override def getReader: FinalAccountStateReader = this

  override def getView: FinalAccountView = {
    new FinalAccountView(stateStorage.getView(), ....)
  }

}


object Main extends App {
  val state: FinalState = new FinalState
  test2()

  def test1(): Unit = {
    val view: FinalAccountView = state.getView
    view.savepoint()
    view.applyMainchainBlockReferenceData(null) match {
      case Success(v) =>
        val newVersion: String = "v1"
        v.commit(VersionTag @@ newVersion)
      case Failure(exception) =>
        view.rollbackToSavepoint()
    }
  }

  def test2(): Unit = {
    val reader: FinalAccountStateReader = state.getReader
    val res: Int = reader.getSomething
    System.out.println(res)
  }

}*/