package io.horizen.account.sc2sc

import com.google.common.primitives.Bytes
import io.horizen.account.state.events.AddCrossChainRedeemMessage
import io.horizen.account.state.{BaseAccountStateView, ExecutionRevertedException, Message, NativeSmartContractMsgProcessor}
import io.horizen.cryptolibprovider.Sc2scCircuit
import io.horizen.evm.Address
import io.horizen.sc2sc.{CrossChainMessage, CrossChainMessageHash}
import io.horizen.utils.BytesUtils
import sparkz.crypto.hash.Keccak256

import java.nio.charset.StandardCharsets

trait CrossChainRedeemMessageProvider {
  def doesCrossChainMessageHashFromRedeemMessageExist(hash: CrossChainMessageHash, view: BaseAccountStateView): Boolean
}

abstract class AbstractCrossChainRedeemMessageProcessor(
                                                         scId: Array[Byte],
                                                         path: Option[String],
                                                         sc2scCircuit: Sc2scCircuit,
                                                         scTxMsgProc: ScTxCommitmentTreeRootHashMessageProvider
                                                       ) extends NativeSmartContractMsgProcessor with CrossChainRedeemMessageProvider {
  protected def processRedeemMessage(accCcRedeemMessage: AccountCrossChainRedeemMessage, view: BaseAccountStateView): Array[Byte] = {
    val accCcMsg = AccountCrossChainMessage(
      accCcRedeemMessage.messageType, accCcRedeemMessage.sender, accCcRedeemMessage.receiverSidechain, accCcRedeemMessage.receiver, accCcRedeemMessage.payloadHash
    )
    val ccMsg = AbstractCrossChainMessageProcessor.buildCrossChainMessageFromAccount(
      accCcMsg,
      scId
    )

    validateRedeemMsg(accCcRedeemMessage, ccMsg, view)

    addCrossChainMessageToView(view, ccMsg)

    addCrossChainRedeemMessageLogEvent(view, accCcRedeemMessage)

    accCcRedeemMessage.encode()
  }

  private def addCrossChainRedeemMessageLogEvent(view: BaseAccountStateView, accCcRedeemMessage: AccountCrossChainRedeemMessage): Unit = {
    val event = AddCrossChainRedeemMessage(
      new Address(accCcRedeemMessage.sender),
      accCcRedeemMessage.messageType,
      accCcRedeemMessage.receiverSidechain,
      accCcRedeemMessage.receiver,
      accCcRedeemMessage.payloadHash,
      accCcRedeemMessage.certificateDataHash,
      accCcRedeemMessage.nextCertificateDataHash,
      accCcRedeemMessage.scCommitmentTreeRoot,
      accCcRedeemMessage.nextScCommitmentTreeRoot
    )
    val evmLog = getEthereumConsensusDataLog(event)
    view.addLog(evmLog)
  }

  private def addCrossChainMessageToView(view: BaseAccountStateView, ccMsg: CrossChainMessage): Unit = {
    val messageHash = ccMsg.getCrossChainMessageHash
    setCrossChainMessageHash(messageHash, view)
  }

  /**
   * Extract the AccountCrossChainRedeemMessage from the message
   *
   * @param msg message to apply to the state
   * @return the AccountCrossChainRedeemMessage built from the arguments in the message
   */
  protected def getAccountCrossChainRedeemMessageFromMessage(msg: Message): AccountCrossChainRedeemMessage

  private def validateRedeemMsg(ccRedeemMgs: AccountCrossChainRedeemMessage, ccMsg: CrossChainMessage, view: BaseAccountStateView): Unit = {
    try {
      // Validate the receiving sidechain matches with this scId
      validateScId(scId, ccRedeemMgs.receiverSidechain)

      // Validate message has not been redeemed yet
      validateDoubleMessageRedeem(ccMsg, view)

      // Validate scCommitmentTreeRoot and nextScCommitmentTreeRoot exists
      validateScCommitmentTreeRoot(ccRedeemMgs.scCommitmentTreeRoot, ccRedeemMgs.nextScCommitmentTreeRoot, view)

      // Validate Proof
      validateProof(ccRedeemMgs)
    } catch {
      case exception: IllegalArgumentException =>
        throw new ExecutionRevertedException("Error while validating redeem message", exception)
    }
  }

  private def validateScId(scId: Array[Byte], receivingScId: Array[Byte]): Unit = {
    if (!scId.sameElements(receivingScId)) {
      throw new IllegalArgumentException(s"This scId `${BytesUtils.toHexString(scId)}` and receiving scId `${BytesUtils.toHexString(receivingScId)}` do not match")
    }
  }

  private def validateDoubleMessageRedeem(ccMsg: CrossChainMessage, view: BaseAccountStateView): Unit = {
    val currentMsgHash = ccMsg.getCrossChainMessageHash
    val ccMsgFromRedeemAlreadyExists = view.doesCrossChainMessageHashFromRedeemMessageExist(currentMsgHash)
    if (ccMsgFromRedeemAlreadyExists) {
      throw new IllegalArgumentException(s"Message $ccMsg has already been redeemed")
    }
  }

  private def validateScCommitmentTreeRoot(
                                            scCommitmentTreeRoot: Array[Byte],
                                            nextScCommitmentTreeRoot: Array[Byte],
                                            view: BaseAccountStateView): Unit = {
        if (!scTxMsgProc.doesScTxCommitmentTreeRootHashExist(scCommitmentTreeRoot, view)) {
          throw new IllegalArgumentException(s"Sidechain commitment tree root `${BytesUtils.toHexString(scCommitmentTreeRoot)}` does not exist")
        }

        if (!scTxMsgProc.doesScTxCommitmentTreeRootHashExist(nextScCommitmentTreeRoot, view)) {
          throw new IllegalArgumentException(s"Sidechain next commitment tree root `${BytesUtils.toHexString(nextScCommitmentTreeRoot)}` does not exist")
        }
  }

  private def validateProof(ccRedeemMessage: AccountCrossChainRedeemMessage): Unit = {
    val accCcMsg = AccountCrossChainMessage(
      ccRedeemMessage.messageType, ccRedeemMessage.sender, ccRedeemMessage.receiverSidechain, ccRedeemMessage.receiver, ccRedeemMessage.payloadHash
    )
    val ccMsg = AbstractCrossChainMessageProcessor.buildCrossChainMessageFromAccount(accCcMsg, scId)
    val ccMsgHash = ccMsg.getCrossChainMessageHash
    val isProofValid = sc2scCircuit.verifyRedeemProof(
      ccMsgHash,
      ccRedeemMessage.scCommitmentTreeRoot,
      ccRedeemMessage.nextScCommitmentTreeRoot,
      ccRedeemMessage.proof,
      path.get
    )

    if (!isProofValid) {
      throw new IllegalArgumentException(s"Cannot verify this cross-chain message $accCcMsg")
    }
  }

  private def calculateKey(keySeed: Array[Byte]): Array[Byte] =
    Keccak256.hash(keySeed)

  private[sc2sc] def getCrossChainMessageFromRedeemKey(hash: Array[Byte]): Array[Byte] =
    calculateKey(Bytes.concat("crossChainMessageFromRedeem".getBytes(StandardCharsets.UTF_8), hash))

  private def setCrossChainMessageHash(messageHash: CrossChainMessageHash, view: BaseAccountStateView): Unit =
    view.updateAccountStorage(contractAddress, getCrossChainMessageFromRedeemKey(messageHash.getValue), messageHash.getValue)

  override def doesCrossChainMessageHashFromRedeemMessageExist(hash: CrossChainMessageHash, view: BaseAccountStateView): Boolean =
    view.getAccountStorage(contractAddress, getCrossChainMessageFromRedeemKey(hash.getValue)).nonEmpty
}