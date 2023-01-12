package com.horizen.account.sc2sc

import java.math.BigInteger
import com.google.common.primitives.Bytes
import com.horizen.account.abi.ABIUtil.getFunctionSignature
import com.horizen.account.state.{AccountStateViewGasTracked,  BaseAccountStateView, BlockContext, ExecutionFailedException, ExecutionRevertedException, GasPool, Message, MessageProcessorFixture, WithdrawalMsgProcessor}
import com.horizen.params.NetworkParams
import com.horizen.proposition.MCPublicKeyHashProposition
import com.horizen.utils.BytesUtils
import scorex.crypto.hash.Keccak256

trait CrossChainMessageProcessorFixture extends MessageProcessorFixture {

  val mcAddr = new MCPublicKeyHashProposition(randomBytes(20))

  def getMessageProcessorTestImpl(networkParams: NetworkParams) : AbstractCrossChainMessageProcessor = {
     new CrossChainMessageProcessorTestImpl(networkParams)
  }

  def listOfCrosschainMessages(epochNun: Int): Message = {
    val params = GetListOfCrosschainMessagesInputCmd(epochNun).encode()
    val data = Bytes.concat(BytesUtils.fromHexString(CrossChainMessageProcessorTestImpl.GetListOfCrosschainMessagesCmdSig), params)
    getMessage(CrossChainMessageProcessorTestImpl.contractAddress, BigInteger.ZERO, data)
  }
}

class CrossChainMessageProcessorTestImpl(networkParams: NetworkParams)  extends AbstractCrossChainMessageProcessor(networkParams)  {

  override val contractAddress: Array[Byte] = CrossChainMessageProcessorTestImpl.contractAddress
  override val contractCode: Array[Byte] =   CrossChainMessageProcessorTestImpl.contractCode

  @throws(classOf[ExecutionFailedException])
  override def process(msg: Message, view: BaseAccountStateView, gas: GasPool, blockContext: BlockContext): Array[Byte] = {
    val gasView = new AccountStateViewGasTracked(view, gas)
    getFunctionSignature(msg.getData) match {
      case CrossChainMessageProcessorTestImpl.GetListOfCrosschainMessagesCmdSig =>
        execGetListOfCrosschainMessages(msg, gasView)
      case functionSig =>
        throw new ExecutionRevertedException(s"Requested function does not exist. Function signature: $functionSig")
    }
  }


}

object CrossChainMessageProcessorTestImpl extends CrossChainMessageProcessorConstants {
  override val contractAddress: Array[Byte] = BytesUtils.fromHexString("0000000000000000000001111111111111111111")
  override val contractCode: Array[Byte] = Keccak256.hash("CrossChainMessageProcessorTestImplCode")
}


