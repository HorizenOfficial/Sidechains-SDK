package io.horizen.account.state

import io.horizen.account.abi.{ABIDecoder, ABIEncodable, MsgProcessorInputDecoder}
import io.horizen.evm.Address
import org.web3j.abi.TypeReference
import org.web3j.abi.datatypes.{DynamicStruct, Type, Utf8String, Address => AbiAddress}
import java.util

case class InvokeSmartContractCmdInput(
            contractAddress: Address,
            dataStr: String) extends ABIEncodable[DynamicStruct] {

  // TODO require this is a valid hex string with minimum 4 bytes
  // require(data..., s"Invalid smart contract data: $dataStr")

  override def asABIType(): DynamicStruct = {

    val listOfParams: util.List[Type[_]] = util.Arrays.asList(
      new AbiAddress(contractAddress.toString),
      new Utf8String(dataStr)
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
      new TypeReference[Utf8String]() {}
    ))

   override def createType(listOfParams: util.List[Type[_]]): InvokeSmartContractCmdInput = {
    val contractAddress = new Address(listOfParams.get(0).asInstanceOf[AbiAddress].toString)
    val dataStr = listOfParams.get(1).asInstanceOf[Utf8String].getValue

    InvokeSmartContractCmdInput(contractAddress, dataStr)
  }

}


