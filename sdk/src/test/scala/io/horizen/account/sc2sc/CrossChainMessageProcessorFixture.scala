package io.horizen.account.sc2sc

import com.google.common.primitives.Bytes
import io.horizen.account.abi.ABIUtil.getFunctionSignature
import io.horizen.account.state._
import io.horizen.evm.Address
import io.horizen.params.NetworkParams
import io.horizen.proposition.MCPublicKeyHashProposition
import io.horizen.utils.BytesUtils
import sparkz.crypto.hash.Keccak256

import java.math.BigInteger

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

  override val contractAddress: Address = CrossChainMessageProcessorTestImpl.contractAddress
  override val contractCode: Array[Byte] =   CrossChainMessageProcessorTestImpl.contractCode

  @throws(classOf[ExecutionFailedException])
  override def process(msg: Message, view: BaseAccountStateView, gas: GasPool, blockContext: BlockContext): Array[Byte] = {
    val gasView = view.getGasTrackedView(gas)
    getFunctionSignature(msg.getData) match {
      case CrossChainMessageProcessorTestImpl.GetListOfCrosschainMessagesCmdSig =>
        execGetListOfCrosschainMessages(msg, gasView)
      case functionSig =>
        throw new ExecutionRevertedException(s"Requested function does not exist. Function signature: $functionSig")
    }
  }


}

object CrossChainMessageProcessorTestImpl extends CrossChainMessageProcessorConstants {
  override val contractAddress: Address = new Address("0x35fdd51e73221f467b40946c97791a3e19799bea")
  override val contractCode: Array[Byte] = Keccak256.hash("CrossChainMessageProcessorTestImplCode")
}


