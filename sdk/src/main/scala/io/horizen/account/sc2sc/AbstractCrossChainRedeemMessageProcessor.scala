package io.horizen.account.sc2sc

import com.google.common.primitives.Bytes
import io.horizen.account.state.events.AddCrossChainRedeemMessage
import io.horizen.account.state.{BaseAccountStateView, Message, NativeSmartContractMsgProcessor}
import io.horizen.cryptolibprovider.utils.FieldElementUtils
import io.horizen.cryptolibprovider.{CryptoLibProvider, Sc2scCircuit}
import io.horizen.params.NetworkParams
import io.horizen.sc2sc.{CrossChainMessage, CrossChainMessageHash}
import io.horizen.utils.BytesUtils
import sparkz.crypto.hash.Keccak256

trait CrossChainRedeemMessageProvider {
  def addScTxCommitmentTreeRootHash(hash: Array[Byte], view: BaseAccountStateView): Unit
  def doesCrossChainMessageHashFromRedeemMessageExist(hash: CrossChainMessageHash, view: BaseAccountStateView): Boolean
}

abstract class AbstractCrossChainRedeemMessageProcessor(
                                                         networkParams: NetworkParams,
                                                         sc2scCircuit: Sc2scCircuit
                                                       ) extends NativeSmartContractMsgProcessor with CrossChainRedeemMessageProvider {
  protected def processRedeemMessage(msg: AccountCrossChainRedeemMessage, view: BaseAccountStateView): Array[Byte] = {
    validateRedeemMsg(msg, view)

    addCrossChainMessageToView(view, msg)

    addCrossChainRedeemMessageLogEvent(view, msg)

    msg.encode()
  }

  private def addCrossChainRedeemMessageLogEvent(view: BaseAccountStateView, accCcRedeemMessage: AccountCrossChainRedeemMessage): Unit = {
    val event = AddCrossChainRedeemMessage(
      accCcRedeemMessage.accountCrossChainMessage,
      accCcRedeemMessage.certificateDataHash,
      accCcRedeemMessage.nextCertificateDataHash,
      accCcRedeemMessage.scCommitmentTreeRoot,
      accCcRedeemMessage.nextScCommitmentTreeRoot,
      accCcRedeemMessage.proof
    )
    val evmLog = getEthereumConsensusDataLog(event)
    view.addLog(evmLog)
  }

  private def addCrossChainMessageToView(view: BaseAccountStateView, accCcRedeemMessage: AccountCrossChainRedeemMessage): Unit = {
    val messageHash = CryptoLibProvider.sc2scCircuitFunctions.getCrossChainMessageHash(
      AbstractCrossChainMessageProcessor.buildCrosschainMessageFromAccount(accCcRedeemMessage.accountCrossChainMessage, networkParams)
    )
    setCrossChainMessageHash(messageHash, view)
  }

  /**
   * Extract the AccountCrossChainRedeemMessage from the message
   *
   * @param msg message to apply to the state
   * @return the AccountCrossChainRedeemMessage built from the arguments in the message
   */
  protected def getAccountCrossChainRedeemMessageFromMessage(msg: Message): AccountCrossChainRedeemMessage

  private def validateRedeemMsg(ccRedeemMgs: AccountCrossChainRedeemMessage, view: BaseAccountStateView): Unit = {
    val accountCcMsg = ccRedeemMgs.accountCrossChainMessage
    // Validate the receiving sidechain matches with this scId
    validateScId(networkParams.sidechainId, accountCcMsg.receiverSidechain)

    // Validate message has not been redeemed yet
    val ccMsg = AbstractCrossChainMessageProcessor.buildCrosschainMessageFromAccount(accountCcMsg, networkParams)
    validateDoubleMessageRedeem(ccMsg, view)

    // Validate scCommitmentTreeRoot and nextScCommitmentTreeRoot exists
    validateScCommitmentTreeRoot(ccRedeemMgs.scCommitmentTreeRoot, ccRedeemMgs.nextScCommitmentTreeRoot, view)

    // Validate Proof
    validateProof(ccRedeemMgs)
  }

  private def validateScId(scId: Array[Byte], receivingScId: Array[Byte]): Unit = {
    if (!scId.sameElements(receivingScId)) {
      throw new IllegalArgumentException(s"This scId `${BytesUtils.toHexString(scId)}` and receiving scId `${BytesUtils.toHexString(receivingScId)}` do not match")
    }
  }

  private def validateDoubleMessageRedeem(ccMsg: CrossChainMessage, view: BaseAccountStateView): Unit = {
    val currentMsgHash = sc2scCircuit.getCrossChainMessageHash(ccMsg)
    val ccMsgFromRedeemAlreadyExists = doesCrossChainMessageHashFromRedeemMessageExist(currentMsgHash, view)
    if (ccMsgFromRedeemAlreadyExists) {
      throw new IllegalArgumentException(s"Message $ccMsg has already been redeemed")
    }
  }

  override def addScTxCommitmentTreeRootHash(hash: Array[Byte], view: BaseAccountStateView): Unit =
    view.updateAccountStorage(contractAddress, getScTxCommitmentTreeRootHashKey(hash), Array.emptyByteArray)

  private def validateScCommitmentTreeRoot(
                                            scCommitmentTreeRoot: Array[Byte],
                                            nextScCommitmentTreeRoot: Array[Byte],
                                            view: BaseAccountStateView): Unit = {
    if (view.getAccountStorage(contractAddress, getScTxCommitmentTreeRootHashKey(scCommitmentTreeRoot)).isEmpty) {
      throw new IllegalArgumentException(s"Sidechain commitment tree root `${BytesUtils.toHexString(scCommitmentTreeRoot)}` does not exist")
    }

    if (view.getAccountStorage(contractAddress, getScTxCommitmentTreeRootHashKey(nextScCommitmentTreeRoot)).isEmpty) {
      throw new IllegalArgumentException(s"Sidechain next commitment tree root `${BytesUtils.toHexString(nextScCommitmentTreeRoot)}` does not exist")
    }
  }

  private def validateProof(ccRedeemMessage: AccountCrossChainRedeemMessage): Unit = {
    val accCcMsg = ccRedeemMessage.accountCrossChainMessage
    val ccMsgHash = sc2scCircuit.getCrossChainMessageHash(
      AbstractCrossChainMessageProcessor.buildCrosschainMessageFromAccount(accCcMsg, networkParams)
    )
    val isProofValid = sc2scCircuit.verifyRedeemProof(
      ccMsgHash,
      ccRedeemMessage.scCommitmentTreeRoot,
      ccRedeemMessage.nextScCommitmentTreeRoot,
      ccRedeemMessage.proof
    )

    if (!isProofValid) {
      throw new IllegalArgumentException(s"Cannot verify this cross-chain message $accCcMsg")
    }
  }

  private def calculateKey(keySeed: Array[Byte]): Array[Byte] =
    Keccak256.hash(keySeed)

  private[sc2sc] def getCrossChainMessageFromRedeemKey(hash: Array[Byte]): Array[Byte] =
    calculateKey(Bytes.concat("crossChainMessageFromRedeem".getBytes, hash))

  private[sc2sc] def getScTxCommitmentTreeRootHashKey(hash: Array[Byte]): Array[Byte] =
    calculateKey(Bytes.concat("scCommitmentTreeRootHash".getBytes, hash))

  private def setCrossChainMessageHash(messageHash: CrossChainMessageHash, view: BaseAccountStateView): Unit =
    view.updateAccountStorage(contractAddress, getCrossChainMessageFromRedeemKey(messageHash.getValue), Array.emptyByteArray)

  override def doesCrossChainMessageHashFromRedeemMessageExist(hash: CrossChainMessageHash, view: BaseAccountStateView): Boolean =
    view.getAccountStorage(contractAddress, getCrossChainMessageFromRedeemKey(hash.getValue)).nonEmpty
}