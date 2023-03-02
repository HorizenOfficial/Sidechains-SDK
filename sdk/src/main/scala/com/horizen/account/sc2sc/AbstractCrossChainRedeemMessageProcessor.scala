package com.horizen.account.sc2sc

import com.horizen.account.events.AddCrossChainRedeemMessage
import com.horizen.account.sc2sc.AbstractCrossChainRedeemMessageProcessor.Constant.CrossChainMessageHashKey
import com.horizen.account.state._
import com.horizen.cryptolibprovider.utils.FieldElementUtils
import com.horizen.cryptolibprovider.{CryptoLibProvider, Sc2scCircuit}
import com.horizen.merkletreenative.MerklePath
import com.horizen.params.NetworkParams
import com.horizen.sc2sc.{CrossChainMessage, CrossChainMessageHash}
import com.horizen.utils.BytesUtils
import sparkz.crypto.hash.Keccak256

trait CrossChainRedeemMessageProvider {
  def doesScTxCommitmentTreeRootExist(hash: Array[Byte], view: BaseAccountStateView): Boolean

  def doesCrossChainMessageHashFromRedeemMessageExist(hash: CrossChainMessageHash, view: BaseAccountStateView): Boolean
}

abstract class AbstractCrossChainRedeemMessageProcessor(
                                                         networkParams: NetworkParams,
                                                         sc2scCircuit: Sc2scCircuit
                                                       ) extends NativeSmartContractMsgProcessor with CrossChainRedeemMessageProvider {
  /**
   * Apply message to the given view. Possible results:
   * <ul>
   * <li>applied as expected: return byte[]</li>
   * <li>message valid and (partially) executed, but operation "failed": throw ExecutionFailedException</li>
   * <li>message invalid and must not exist in a block: throw any other Exception</li>
   * </ul>
   *
   * @param msg          message to apply to the state
   * @param view         state view
   * @param gas          available gas for the execution
   * @param blockContext contextual information accessible during execution
   * @return return data on successful execution
   */
  @throws(classOf[ExecutionFailedException])
  override def process(msg: Message, view: BaseAccountStateView, gas: GasPool, blockContext: BlockContext): Array[Byte] = {
    val accCcRedeemMessage = getAccountCrossChainRedeemMessageFromMessage(msg)

    validateRedeemMsg(accCcRedeemMessage, view)
    processHook(msg, view, gas, blockContext)
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

    validateMsgHook()
  }

  private def validateScId(scId: Array[Byte], receivingScId: Array[Byte]): Unit = {
    if (!scId.sameElements(receivingScId)) {
      throw new IllegalArgumentException(s"This scId `${BytesUtils.toHexString(scId)}` and receiving scId `${BytesUtils.toHexString(receivingScId)}` do not match")
    }
  }

  private def validateDoubleMessageRedeem(ccMsg: CrossChainMessage, view: BaseAccountStateView): Unit = {
    val currentMsgHash = sc2scCircuit.getCrossChainMessageHash(ccMsg)
    val ccMsgFromRedeemAlreadyExists = view.doesCrossChainMessageHashFromRedeemMessageExist(currentMsgHash)
    if (ccMsgFromRedeemAlreadyExists) {
      throw new IllegalArgumentException(s"Message $ccMsg has already been redeemed")
    }
  }

  private def validateScCommitmentTreeRoot(
                                            scCommitmentTreeRoot: Array[Byte],
                                            nextScCommitmentTreeRoot: Array[Byte],
                                            view: BaseAccountStateView): Unit = {
    if (!view.doesScTxCommitmentTreeRootExist(scCommitmentTreeRoot)) {
      throw new IllegalArgumentException(s"Sidechain commitment tree root `${BytesUtils.toHexString(scCommitmentTreeRoot)}` does not exist")
    }

    if (!view.doesScTxCommitmentTreeRootExist(nextScCommitmentTreeRoot)) {
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
      FieldElementUtils.messageToFieldElement(ccRedeemMessage.scCommitmentTreeRoot),
      FieldElementUtils.messageToFieldElement(ccRedeemMessage.nextScCommitmentTreeRoot),
      MerklePath.deserialize(ccRedeemMessage.certificateDataHash),
      MerklePath.deserialize(ccRedeemMessage.nextCertificateDataHash),
      ccRedeemMessage.proof
    )

    if (!isProofValid) {
      throw new IllegalArgumentException(s"Cannot verify this cross-chain message $accCcMsg")
    }
  }

  /**
   * A hook to add custom validation logic
   */
  protected def validateMsgHook(): Unit

  /**
   * A hook to define the process logic after the message has been validated
   *
   * @param msg          message to apply to the state
   * @param view         state view
   * @param gas          available gas for the execution
   * @param blockContext contextual information accessible during execution
   * @return return data on successful execution
   */
  protected def processHook(msg: Message, view: BaseAccountStateView, gas: GasPool, blockContext: BlockContext): Array[Byte]

  protected def addCrossChainRedeemMessage(
                                            accountCrossChainMessage: AccountCrossChainMessage,
                                            certificateDataHash: Array[Byte],
                                            nextCertificateDataHash: Array[Byte],
                                            scCommitmentTreeRoot: Array[Byte],
                                            nextScCommitmentTreeRoot: Array[Byte],
                                            proof: Array[Byte],
                                            view: AccountStateView
                                          ): Array[Byte] = {
    val redeemMessage = AccountCrossChainRedeemMessage(
      accountCrossChainMessage, certificateDataHash, nextCertificateDataHash, scCommitmentTreeRoot, nextScCommitmentTreeRoot, proof
    )

    val messageHash = CryptoLibProvider.sc2scCircuitFunctions.getCrossChainMessageHash(
      AbstractCrossChainMessageProcessor.buildCrosschainMessageFromAccount(accountCrossChainMessage, networkParams)
    )
    setCrossChaimMessageHash(messageHash, view)

    val event = AddCrossChainRedeemMessage(
      accountCrossChainMessage, certificateDataHash, nextCertificateDataHash, scCommitmentTreeRoot, nextScCommitmentTreeRoot, proof
    )
    val evmLog = getEvmLog(event)
    view.addLog(evmLog)

    redeemMessage.encode()
  }

  private def getCrossChainMessageHashKey: Array[Byte] =
    calculateKey(CrossChainMessageHashKey.getBytes)

  private[horizen] def calculateKey(keySeed: Array[Byte]): Array[Byte] =
    Keccak256.hash(keySeed)

  private def setCrossChaimMessageHash(messageHash: CrossChainMessageHash, view: AccountStateView): Unit =
    view.updateAccountStorage(contractAddress, getCrossChainMessageHashKey, messageHash.bytes)

  override def doesScTxCommitmentTreeRootExist(hash: Array[Byte], view: BaseAccountStateView): Boolean =
    view.doesScTxCommitmentTreeRootExist(hash)

  override def doesCrossChainMessageHashFromRedeemMessageExist(hash: CrossChainMessageHash, view: BaseAccountStateView): Boolean =
    view.doesCrossChainMessageHashFromRedeemMessageExist(hash)
}

object AbstractCrossChainRedeemMessageProcessor {
  object Constant {
    val CrossChainMessageHashKey = "crossChainMsgHashKey"
  }
}