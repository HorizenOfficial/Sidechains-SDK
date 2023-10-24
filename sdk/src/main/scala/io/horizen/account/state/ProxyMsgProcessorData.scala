package io.horizen.account.state

import io.horizen.account.abi.{ABIDecoder, ABIEncodable, MsgProcessorInputDecoder}
import io.horizen.evm.Address
import io.horizen.utils.BytesUtils
import org.web3j.abi.TypeReference
import org.web3j.abi.datatypes.{DynamicBytes, DynamicStruct, Type, Address => AbiAddress}

import java.util

case class InvokeSmartContractCmdInput(
            contractAddress: Address,
            dataStr: String) extends ABIEncodable[DynamicStruct] {

 
  override def asABIType(): DynamicStruct = {

    val dataBytes: Array[Byte] = org.web3j.utils.Numeric.hexStringToByteArray(dataStr)
    val listOfParams: util.List[Type[_]] = util.Arrays.asList(
      new AbiAddress(contractAddress.toString),
      new DynamicBytes(dataBytes)
    )
    new DynamicStruct(listOfParams)
  }

  override def toString: String = "%s(contractAddress: %s, data: %s)"
    .format(this.getClass.toString, contractAddress.toString, dataStr)
}

object InvokeSmartContractCmdInputDecoder
  extends ABIDecoder[InvokeSmartContractCmdInput]
    with MsgProcessorInputDecoder[InvokeSmartContractCmdInput] {

  override val getListOfABIParamTypes: util.List[TypeReference[Type[_]]] =
    org.web3j.abi.Utils.convert(util.Arrays.asList(
      new TypeReference[AbiAddress]() {},
      new TypeReference[DynamicBytes]() {}
    ))

   override def createType(listOfParams: util.List[Type[_]]): InvokeSmartContractCmdInput = {
    val contractAddress = new Address(listOfParams.get(0).asInstanceOf[AbiAddress].toString)
    val dataBytes = listOfParams.get(1).asInstanceOf[DynamicBytes].getValue

    InvokeSmartContractCmdInput(contractAddress, BytesUtils.toHexString(dataBytes))
  }

}


