package io.horizen.account.state.nativescdata.forgerstakev2

import io.horizen.account.abi.{ABIDecoder, ABIEncodable, MsgProcessorInputDecoder}
import io.horizen.account.state.{AddNewStakeCmdInput, ForgerPublicKeys}
import io.horizen.evm.Address
import io.horizen.proposition.{PublicKey25519Proposition, VrfPublicKey}
import org.web3j.abi.TypeReference
import org.web3j.abi.datatypes.generated.{Bytes1, Bytes32, Int32, Uint256, Uint32}
import org.web3j.abi.datatypes.{StaticStruct, Type, Address => AbiAddress}

import java.util

object UpgradeCmdInputDecoder
  extends ABIDecoder[UpgradeCmdInput]
    with MsgProcessorInputDecoder[UpgradeCmdInput] {

  override val getListOfABIParamTypes: util.List[TypeReference[Type[_]]] =
    org.web3j.abi.Utils.convert(util.Arrays.asList(
      new TypeReference[Int32]() {}
      ))

  override def createType(listOfParams: util.List[Type[_]]): UpgradeCmdInput = {
    val version = listOfParams.get(0).asInstanceOf[Int32].getValue
    UpgradeCmdInput(version.intValueExact())
  }

}

case class UpgradeCmdInput(newVersion: Int) extends ABIEncodable[StaticStruct] {

  override def asABIType(): StaticStruct = {
    val listOfParams: util.List[Type[_]] = util.Arrays.asList(
      new Int32(newVersion)
    )
    new StaticStruct(listOfParams)
  }

  override def toString: String = "%s(newVersion: %s)"
    .format(this.getClass.toString, newVersion)
}
