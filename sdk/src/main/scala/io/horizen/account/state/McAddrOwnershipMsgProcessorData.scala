package io.horizen.account.state

import com.fasterxml.jackson.annotation.JsonView
import io.horizen.account.abi.{ABIDecoder, ABIEncodable, ABIListEncoder, MsgProcessorInputDecoder}
import io.horizen.account.state.McAddrOwnershipData.decodeMcSignature
import io.horizen.json.Views
import io.horizen.evm.Address
import io.horizen.utils.BytesUtils
import org.web3j.abi.TypeReference
import org.web3j.abi.datatypes.generated.{Bytes24, Bytes32}
import org.web3j.abi.datatypes.{DynamicStruct, StaticStruct, Type, Utf8String, Address => AbiAddress}
import sparkz.core.serialization.{BytesSerializable, SparkzSerializer}
import sparkz.util.serialization.{Reader, Writer}

import java.nio.charset.StandardCharsets
import java.util


case class AddNewOwnershipCmdInput(mcTransparentAddress: String, mcSignature: String)
  extends ABIEncodable[DynamicStruct] {

  require(mcTransparentAddress.length == BytesUtils.HORIZEN_MC_TRANSPARENT_ADDRESS_BASE_58_LENGTH,
    s"Invalid mc address length: ${mcTransparentAddress.length}")

  require(mcSignature.length == BytesUtils.HORIZEN_MC_SIGNATURE_BASE_64_LENGTH,
    s"Invalid mc signature length: ${mcSignature.length}")

  override def asABIType(): DynamicStruct = {

    val mcSignatureBytes = mcSignature.getBytes(StandardCharsets.UTF_8)

    val listOfParams: util.List[Type[_]] = util.Arrays.asList(
      new Utf8String(mcTransparentAddress),
      new Bytes24(util.Arrays.copyOfRange(mcSignatureBytes, 0, 24)),
      new Bytes32(util.Arrays.copyOfRange(mcSignatureBytes, 24, 56)),
      new Bytes32(util.Arrays.copyOfRange(mcSignatureBytes, 56, BytesUtils.HORIZEN_MC_SIGNATURE_BASE_64_LENGTH)))
    new DynamicStruct(listOfParams)

  }

  override def toString: String = "%s(mcAddress: %s, signature: %s)"
    .format(
      this.getClass.toString,
      mcTransparentAddress, mcSignature)
}

object AddNewOwnershipCmdInputDecoder
  extends ABIDecoder[AddNewOwnershipCmdInput]
    with MsgProcessorInputDecoder[AddNewOwnershipCmdInput] {

  override def getABIDataParamsDynamicLengthInBytes: Int =
    Type.MAX_BYTE_LENGTH + // offset of the dynamic utf8string (it is placed at the end of the struct)
      3 * Type.MAX_BYTE_LENGTH + // three chiunks of 32 bytes for 88 bytes signature
        Type.MAX_BYTE_LENGTH + // the 32 padded utf8String size
          2 * Type.MAX_BYTE_LENGTH  // two chunks for the 35 bytes mc address string

  override val getListOfABIParamTypes: util.List[TypeReference[Type[_]]] = {
    org.web3j.abi.Utils.convert(util.Arrays.asList(
      // mc transparent address
      new TypeReference[Utf8String]() {},
      // signature
      new TypeReference[Bytes24]() {},
      new TypeReference[Bytes32]() {},
      new TypeReference[Bytes32]() {}))
  }


  override def createType(listOfParams: util.List[Type[_]]): AddNewOwnershipCmdInput = {
    val mcTransparentAddress = listOfParams.get(0).asInstanceOf[Utf8String].getValue
    val mcSignature = decodeMcSignature(
      listOfParams.get(1).asInstanceOf[Bytes24],
      listOfParams.get(2).asInstanceOf[Bytes32],
      listOfParams.get(3).asInstanceOf[Bytes32])

    AddNewOwnershipCmdInput(mcTransparentAddress, mcSignature)
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

case class RemoveOwnershipCmdInput(mcTransparentAddressOpt: Option[String])
  extends ABIEncodable[DynamicStruct] {


  override def asABIType(): DynamicStruct = {
    val mcAddrBytes = mcTransparentAddressOpt match {
      case Some(mcTransparentAddress) =>
        require(mcTransparentAddress.length == BytesUtils.HORIZEN_MC_TRANSPARENT_ADDRESS_BASE_58_LENGTH,
          s"Invalid mc address length: ${mcTransparentAddress.length}")
        mcTransparentAddress.getBytes(StandardCharsets.UTF_8)
      case None =>
        new Array[Byte](BytesUtils.HORIZEN_MC_TRANSPARENT_ADDRESS_BASE_58_LENGTH)
    }

    val listOfParams: util.List[Type[_]] = util.Arrays.asList(
      new Utf8String(new String(mcAddrBytes, StandardCharsets.UTF_8))
    )
    new DynamicStruct(listOfParams)
  }

  override def toString: String = "%s(mcAddress: %s)"
    .format(
      this.getClass.toString,
      mcTransparentAddressOpt.getOrElse("undef"))
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

  override def getABIDataParamsDynamicLengthInBytes: Int =
    2 * Type.MAX_BYTE_LENGTH + // Utf8String bytes32PaddedLength
      2 * Type.MAX_BYTE_LENGTH // twochunks of 32 bytes needed for 35 bytes string

  override val getListOfABIParamTypes: util.List[TypeReference[Type[_]]] = {
    org.web3j.abi.Utils.convert(util.Arrays.asList(
      // mc transparent address
      new TypeReference[Utf8String]() {}
    ))
  }

  override def createType(listOfParams: util.List[Type[_]]): RemoveOwnershipCmdInput = {
    val mcTransparentAddress = listOfParams.get(0).asInstanceOf[Utf8String].getValue

    RemoveOwnershipCmdInput(Some(mcTransparentAddress))
  }
}



@JsonView(Array(classOf[Views.Default]))
case class McAddrOwnershipData(scAddress: String, mcTransparentAddress: String)
  extends BytesSerializable  with ABIEncodable[DynamicStruct] {

  require(mcTransparentAddress.length == BytesUtils.HORIZEN_MC_TRANSPARENT_ADDRESS_BASE_58_LENGTH,
    s"Invalid mc address length: ${mcTransparentAddress.length}")

  override type M = McAddrOwnershipData

  override def serializer: SparkzSerializer[McAddrOwnershipData] = McAddrOwnershipDataSerializer

  override def toString: String = "%s(scAddress: %s, mcAddress: %s)"
    .format(this.getClass.toString, scAddress, mcTransparentAddress)

  override def asABIType(): DynamicStruct = {

    val listOfParams: util.List[Type[_]] = util.Arrays.asList(
      new AbiAddress(scAddress),
      new Utf8String(mcTransparentAddress)
    )
    new DynamicStruct(listOfParams)
  }
}

object McAddrOwnershipData {
  def decodeMcSignature(first24Bytes: Bytes24, middle32Bytes: Bytes32, last32Bytes: Bytes32): String = {
    new String(first24Bytes.getValue ++ middle32Bytes.getValue ++ last32Bytes.getValue, StandardCharsets.UTF_8)
  }
}

object McAddrOwnershipDataListEncoder extends ABIListEncoder[McAddrOwnershipData, DynamicStruct]{
  override def getAbiClass: Class[DynamicStruct] = classOf[DynamicStruct]
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
