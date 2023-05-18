package io.horizen.account.state

import com.fasterxml.jackson.annotation.JsonView
import io.horizen.account.abi.{ABIDecoder, ABIEncodable, ABIListEncoder, MsgProcessorInputDecoder}
import io.horizen.account.state.McAddrOwnershipData.{decodeMcAddress, decodeMcSignature}
import io.horizen.json.Views
import io.horizen.evm.Address
import io.horizen.utils.BytesUtils
import org.web3j.abi.TypeReference
import org.web3j.abi.datatypes.generated.{Bytes24, Bytes3, Bytes32}
import org.web3j.abi.datatypes.{StaticStruct, Type, Address => AbiAddress}
import sparkz.core.serialization.{BytesSerializable, SparkzSerializer}
import sparkz.util.serialization.{Reader, Writer}
import java.nio.charset.StandardCharsets
import java.util


case class AddNewOwnershipCmdInput(scAddress: Address, mcTransparentAddress: String, mcSignature: String)
  extends ABIEncodable[StaticStruct] {

  require(mcTransparentAddress.length == BytesUtils.HORIZEN_MC_TRANSPARENT_ADDRESS_BASE_58_LENGTH,
    s"Invalid mc address length: ${mcTransparentAddress.length}")

  require(mcSignature.length == BytesUtils.HORIZEN_MC_SIGNATURE_BASE_64_LENGTH,
    s"Invalid mc signature length: ${mcSignature.length}")

  override def asABIType(): StaticStruct = {


    val mcAddrBytes = mcTransparentAddress.getBytes(StandardCharsets.UTF_8)
    val mcSignatureBytes = mcSignature.getBytes(StandardCharsets.UTF_8)

    val listOfParams: util.List[Type[_]] = util.Arrays.asList(
      new AbiAddress(scAddress.toString),
      new Bytes3(util.Arrays.copyOfRange(mcAddrBytes, 0, 3)),
      new Bytes32(util.Arrays.copyOfRange(mcAddrBytes, 3, BytesUtils.HORIZEN_MC_TRANSPARENT_ADDRESS_BASE_58_LENGTH)),
      new Bytes24(util.Arrays.copyOfRange(mcSignatureBytes, 0, 24)),
      new Bytes32(util.Arrays.copyOfRange(mcSignatureBytes, 24, 56)),
      new Bytes32(util.Arrays.copyOfRange(mcSignatureBytes, 56, BytesUtils.HORIZEN_MC_SIGNATURE_BASE_64_LENGTH)))
    new StaticStruct(listOfParams)

  }

  override def toString: String = "%s(scAddress: %s, mcAddress: %s, signature: %s)"
    .format(
      this.getClass.toString,
      scAddress.toString, mcTransparentAddress, mcSignature)
}

object AddNewOwnershipCmdInputDecoder
  extends ABIDecoder[AddNewOwnershipCmdInput]
    with MsgProcessorInputDecoder[AddNewOwnershipCmdInput] {

  override val getListOfABIParamTypes: util.List[TypeReference[Type[_]]] = {
    org.web3j.abi.Utils.convert(util.Arrays.asList(
      // sc address
      new TypeReference[AbiAddress]() {},
      // mc transparent address
      new TypeReference[Bytes3]() {},
      new TypeReference[Bytes32]() {},
      // signature
      new TypeReference[Bytes24]() {},
      new TypeReference[Bytes32]() {},
      new TypeReference[Bytes32]() {}))
  }


  override def createType(listOfParams: util.List[Type[_]]): AddNewOwnershipCmdInput = {
    val scAddress = new Address(listOfParams.get(0).asInstanceOf[AbiAddress].toString)
    val mcTransparentAddrress = decodeMcAddress(
      listOfParams.get(1).asInstanceOf[Bytes3],
      listOfParams.get(2).asInstanceOf[Bytes32])
    val mcSignature = decodeMcSignature(
      listOfParams.get(3).asInstanceOf[Bytes24],
      listOfParams.get(4).asInstanceOf[Bytes32],
      listOfParams.get(5).asInstanceOf[Bytes32])

    AddNewOwnershipCmdInput(scAddress, mcTransparentAddrress, mcSignature)
  }
}

case class GetOwnershipsCmdInput(scAddress: Address)
  extends ABIEncodable[StaticStruct] {

  override def asABIType(): StaticStruct = {

    val listOfParams: util.List[Type[_]] = util.Arrays.asList(
      new AbiAddress(scAddress.toString)
    )
    new StaticStruct(listOfParams)
  }

  override def toString: String = "%s(scAddress: %s)"
    .format(
      this.getClass.toString,
      scAddress.toString)
}

case class RemoveOwnershipCmdInput(scAddress: Address, mcTransparentAddressOpt: Option[String])
  extends ABIEncodable[StaticStruct] {


  override def asABIType(): StaticStruct = {
    val mcAddrBytes = mcTransparentAddressOpt match {
      case Some(mcTransparentAddress) =>
        require(mcTransparentAddress.length == BytesUtils.HORIZEN_MC_TRANSPARENT_ADDRESS_BASE_58_LENGTH,
          s"Invalid mc address length: ${mcTransparentAddress.length}")
        mcTransparentAddress.getBytes(StandardCharsets.UTF_8)
      case None =>
        new Array[Byte](35)
    }

    val listOfParams: util.List[Type[_]] = util.Arrays.asList(
      new AbiAddress(scAddress.toString),
      new Bytes3(util.Arrays.copyOfRange(mcAddrBytes, 0, 3)),
      new Bytes32(util.Arrays.copyOfRange(mcAddrBytes, 3, BytesUtils.HORIZEN_MC_TRANSPARENT_ADDRESS_BASE_58_LENGTH))
    )
    new StaticStruct(listOfParams)
  }

  override def toString: String = "%s(scAddress: %s, mcAddress: %s)"
    .format(
      this.getClass.toString,
      scAddress.toString, mcTransparentAddressOpt.getOrElse("undef"))
}

object GetOwnershipsCmdInputDecoder
  extends ABIDecoder[GetOwnershipsCmdInput]
    with MsgProcessorInputDecoder[GetOwnershipsCmdInput] {

  override val getListOfABIParamTypes: util.List[TypeReference[Type[_]]] = {
    org.web3j.abi.Utils.convert(util.Arrays.asList(
      new TypeReference[AbiAddress]() {}
    ))
  }

  override def createType(listOfParams: util.List[Type[_]]): GetOwnershipsCmdInput = {
    val scAddress = new Address(listOfParams.get(0).asInstanceOf[AbiAddress].toString)
    GetOwnershipsCmdInput(scAddress)
  }
}

object RemoveOwnershipCmdInputDecoder
  extends ABIDecoder[RemoveOwnershipCmdInput]
    with MsgProcessorInputDecoder[RemoveOwnershipCmdInput] {

  override val getListOfABIParamTypes: util.List[TypeReference[Type[_]]] = {
    org.web3j.abi.Utils.convert(util.Arrays.asList(
      // sc address
      new TypeReference[AbiAddress]() {},
      // mc transparent address
      new TypeReference[Bytes3]() {},
      new TypeReference[Bytes32]() {},
    ))
  }

  override def createType(listOfParams: util.List[Type[_]]): RemoveOwnershipCmdInput = {
    val scAddress = new Address(listOfParams.get(0).asInstanceOf[AbiAddress].toString)
    val mcTransparentAddress =
      decodeMcAddress(listOfParams.get(1).asInstanceOf[Bytes3], listOfParams.get(2).asInstanceOf[Bytes32])

    RemoveOwnershipCmdInput(scAddress, Some(mcTransparentAddress))
  }
}



@JsonView(Array(classOf[Views.Default]))
case class McAddrOwnershipData(scAddress: String, mcTransparentAddress: String)
  extends BytesSerializable  with ABIEncodable[StaticStruct] {

  require(mcTransparentAddress.length == BytesUtils.HORIZEN_MC_TRANSPARENT_ADDRESS_BASE_58_LENGTH,
    s"Invalid mc address length: ${mcTransparentAddress.length}")

  override type M = McAddrOwnershipData

  override def serializer: SparkzSerializer[McAddrOwnershipData] = McAddrOwnershipDataSerializer

  override def toString: String = "%s(scAddress: %s, mcAddress: %s)"
    .format(this.getClass.toString, scAddress, mcTransparentAddress)

  override def asABIType(): StaticStruct = {
    val mcAddrBytes = mcTransparentAddress.getBytes(StandardCharsets.UTF_8)

    val listOfParams: util.List[Type[_]] = util.Arrays.asList(
      new AbiAddress(scAddress),
      new Bytes3(util.Arrays.copyOfRange(mcAddrBytes, 0, 3)),
      new Bytes32(util.Arrays.copyOfRange(mcAddrBytes, 3, BytesUtils.HORIZEN_MC_TRANSPARENT_ADDRESS_BASE_58_LENGTH))
    )
    new StaticStruct(listOfParams)
  }
}

object McAddrOwnershipData {
  def decodeMcAddress(first3Bytes: Bytes3, last32Bytes: Bytes32): String = {
    new String(first3Bytes.getValue ++ last32Bytes.getValue, StandardCharsets.UTF_8)
  }
  def decodeMcSignature(first24Bytes: Bytes24, middle32Bytes: Bytes32, last32Bytes: Bytes32): String = {
    new String(first24Bytes.getValue ++ middle32Bytes.getValue ++ last32Bytes.getValue, StandardCharsets.UTF_8)
  }
}

object McAddrOwnershipDataListEncoder extends ABIListEncoder[McAddrOwnershipData, StaticStruct]{
  override def getAbiClass: Class[StaticStruct] = classOf[StaticStruct]
}

object McAddrOwnershipDataSerializer extends SparkzSerializer[McAddrOwnershipData] {
  override def serialize(s: McAddrOwnershipData, w: Writer): Unit = {
    val scAddressBytes = s.scAddress.getBytes(StandardCharsets.UTF_8)
    val mcAddressBytes = s.mcTransparentAddress.getBytes(StandardCharsets.UTF_8)
    w.putInt(scAddressBytes.length)
    w.putBytes(scAddressBytes)
    w.putInt(mcAddressBytes.length)
    w.putBytes(mcAddressBytes)
  }

  override def parse(r: Reader): McAddrOwnershipData = {
    val scAddressBytesLength = r.getInt()
    val scAddressBytes = r.getBytes(scAddressBytesLength)
    val mcAddressBytesLength = r.getInt()
    val mcAddressBytes = r.getBytes(mcAddressBytesLength)

    McAddrOwnershipData(
      new String(scAddressBytes, StandardCharsets.UTF_8),
      new String(mcAddressBytes, StandardCharsets.UTF_8)
    )
  }
}
