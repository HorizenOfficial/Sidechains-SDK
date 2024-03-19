package io.horizen.account.state.nativescdata.forgerstakev2

import io.horizen.account.abi.{ABIDecoder, ABIEncodable, MsgProcessorInputDecoder}
import io.horizen.evm.Address
import org.web3j.abi.TypeReference
import org.web3j.abi.datatypes.generated.Int32
import org.web3j.abi.datatypes.{StaticStruct, Type, Address => AbiAddress}

import java.util

object PagedForgersStakesByDelegatorCmdInputDecoder
  extends ABIDecoder[PagedForgersStakesByDelegatorCmdInput]
    with MsgProcessorInputDecoder[PagedForgersStakesByDelegatorCmdInput] {

  override val getListOfABIParamTypes: util.List[TypeReference[Type[_]]] =
    org.web3j.abi.Utils.convert(util.Arrays.asList(
      new TypeReference[AbiAddress]() {},
      new TypeReference[Int32]() {},
      new TypeReference[Int32]() {}
    ))

  override def createType(listOfParams: util.List[Type[_]]): PagedForgersStakesByDelegatorCmdInput = {
    val delegator = new Address(listOfParams.get(0).asInstanceOf[AbiAddress].toString)
    val startIndex = listOfParams.get(1).asInstanceOf[Int32].getValue.intValueExact()
    val pageSize = listOfParams.get(2).asInstanceOf[Int32].getValue.intValueExact()
    PagedForgersStakesByDelegatorCmdInput(delegator, startIndex, pageSize)
  }
}

case class PagedForgersStakesByDelegatorCmdInput(delegator: Address, startIndex: Int, pageSize: Int) extends ABIEncodable[StaticStruct] {

  override def asABIType(): StaticStruct = {
    val listOfParams: util.List[Type[_]] = util.Arrays.asList(
      new AbiAddress(delegator.toString),
      new Int32(startIndex),
      new Int32(pageSize))
    new StaticStruct(listOfParams)
  }

  override def toString: String = "%s(delegator: %s)"
    .format(this.getClass.toString, delegator)
}
