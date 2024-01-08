package io.horizen.account.state

import com.fasterxml.jackson.annotation.JsonView
import io.horizen.account.abi.{ABIDecoder, ABIEncodable, ABIListEncoder, MsgProcessorInputDecoder}
import io.horizen.account.state.McAddrOwnershipData.{decodeMcAddress, decodeMcSignature}
import io.horizen.evm.Address
import io.horizen.json.Views
import io.horizen.utils.BytesUtils
import org.web3j.abi.TypeReference
import org.web3j.abi.datatypes.generated.{Bytes24, Bytes3, Bytes32}
import org.web3j.abi.datatypes.{DynamicArray, DynamicStruct, StaticStruct, Type, Utf8String, Address => AbiAddress}
import sparkz.core.serialization.{BytesSerializable, SparkzSerializer}
import sparkz.util.serialization.{Reader, Writer}

import java.nio.charset.StandardCharsets
import java.util
import scala.collection.JavaConverters
import scala.jdk.CollectionConverters.asScalaBufferConverter


case class AddNewOwnershipCmdInput(mcTransparentAddress: String, mcSignature: String)
  extends ABIEncodable[StaticStruct] {

  require(mcTransparentAddress.length == BytesUtils.HORIZEN_MC_TRANSPARENT_ADDRESS_BASE_58_LENGTH,
    s"Invalid mc address length: ${mcTransparentAddress.length}")

  require(mcSignature.length == BytesUtils.HORIZEN_MC_SIGNATURE_BASE_64_LENGTH,
    s"Invalid mc signature length: ${mcSignature.length}")

  override def asABIType(): StaticStruct = {


    val mcAddrBytes = mcTransparentAddress.getBytes(StandardCharsets.UTF_8)
    val mcSignatureBytes = mcSignature.getBytes(StandardCharsets.UTF_8)

    val listOfParams: util.List[Type[_]] = util.Arrays.asList(
      new Bytes3(util.Arrays.copyOfRange(mcAddrBytes, 0, 3)),
      new Bytes32(util.Arrays.copyOfRange(mcAddrBytes, 3, BytesUtils.HORIZEN_MC_TRANSPARENT_ADDRESS_BASE_58_LENGTH)),
      new Bytes24(util.Arrays.copyOfRange(mcSignatureBytes, 0, 24)),
      new Bytes32(util.Arrays.copyOfRange(mcSignatureBytes, 24, 56)),
      new Bytes32(util.Arrays.copyOfRange(mcSignatureBytes, 56, BytesUtils.HORIZEN_MC_SIGNATURE_BASE_64_LENGTH)))
    new StaticStruct(listOfParams)

  }

  override def toString: String = "%s(mcAddress: %s, signature: %s)"
    .format(
      this.getClass.toString,
      mcTransparentAddress, mcSignature)
}

object AddNewOwnershipCmdInputDecoder
  extends ABIDecoder[AddNewOwnershipCmdInput]
    with MsgProcessorInputDecoder[AddNewOwnershipCmdInput] {

  override val getListOfABIParamTypes: util.List[TypeReference[Type[_]]] = {
    org.web3j.abi.Utils.convert(util.Arrays.asList(
      // mc transparent address
      new TypeReference[Bytes3]() {},
      new TypeReference[Bytes32]() {},
      // signature
      new TypeReference[Bytes24]() {},
      new TypeReference[Bytes32]() {},
      new TypeReference[Bytes32]() {}))
  }


  override def createType(listOfParams: util.List[Type[_]]): AddNewOwnershipCmdInput = {
    val mcTransparentAddrress = decodeMcAddress(
      listOfParams.get(0).asInstanceOf[Bytes3],
      listOfParams.get(1).asInstanceOf[Bytes32])
    val mcSignature = decodeMcSignature(
      listOfParams.get(2).asInstanceOf[Bytes24],
      listOfParams.get(3).asInstanceOf[Bytes32],
      listOfParams.get(4).asInstanceOf[Bytes32])

    AddNewOwnershipCmdInput(mcTransparentAddrress, mcSignature)
  }
}

case class AddNewMultisigOwnershipCmdInput(mcTransparentAddress: String, redeemScript: String, mcSignatures: Seq[String])
  extends ABIEncodable[DynamicStruct] {

  require(mcTransparentAddress.length == BytesUtils.HORIZEN_MC_TRANSPARENT_ADDRESS_BASE_58_LENGTH,
    s"Invalid mc address length: ${mcTransparentAddress.length}")

  require(mcSignatures.size <= 16)
  mcSignatures.foreach { s =>
    require(s.length == BytesUtils.HORIZEN_MC_SIGNATURE_BASE_64_LENGTH,
      s"Invalid mc signature length: ${s.length}")
  }

  override def asABIType(): DynamicStruct = {

    val listOfSignatures = JavaConverters.seqAsJavaList(mcSignatures.map(new Utf8String(_)))
    val theType = classOf[Utf8String]

    val listOfParams: util.List[Type[_]] = util.Arrays.asList(
      new Utf8String(mcTransparentAddress),
      new Utf8String(redeemScript),
      new DynamicArray(theType, listOfSignatures))

    new DynamicStruct(listOfParams)
  }

  override def toString: String = {
    val str1 = "%s(mcAddress: %s, redeemScript: %s, signatures:["
      .format(
        this.getClass.toString,
        mcTransparentAddress, redeemScript)
    val str2 = mcSignatures.mkString(":")
    str1 + str2 + "])"
  }
}


object AddNewMultisigOwnershipCmdInputDecoder
  extends ABIDecoder[AddNewMultisigOwnershipCmdInput]
    with MsgProcessorInputDecoder[AddNewMultisigOwnershipCmdInput] {

  /* We should not check this dynamic size
  override def getABIDataParamsDynamicLengthInBytes: Int =
    2*Type.MAX_BYTE_LENGTH + // offsets of the 2 dynamic utf8strings
      Type.MAX_BYTE_LENGTH + // the 32 padded utf8String size
      2 * Type.MAX_BYTE_LENGTH +  // two chunks for the 35 bytes mc address string
      ...
   */

  override val getListOfABIParamTypes: util.List[TypeReference[Type[_]]] = {
    org.web3j.abi.Utils.convert(util.Arrays.asList(
      // mc transparent address
      new TypeReference[Utf8String]() {},
      // redeem script
      new TypeReference[Utf8String]() {},
      // signatures
      new TypeReference[DynamicArray[Utf8String]]() {}))
  }


  override def createType(listOfParams: util.List[Type[_]]): AddNewMultisigOwnershipCmdInput = {
    val mcTransparentAddress = listOfParams.get(0).asInstanceOf[Utf8String].getValue
    val redeemScript = listOfParams.get(1).asInstanceOf[Utf8String].getValue
    val mcSignatures = listOfParams.get(2).asInstanceOf[DynamicArray[Utf8String]].getValue

    AddNewMultisigOwnershipCmdInput(mcTransparentAddress, redeemScript, mcSignatures.asScala.map(_.getValue))
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
  extends ABIEncodable[StaticStruct] {


  override def asABIType(): StaticStruct = {
    val mcAddrBytes = mcTransparentAddressOpt match {
      case Some(mcTransparentAddress) =>
        require(mcTransparentAddress.length == BytesUtils.HORIZEN_MC_TRANSPARENT_ADDRESS_BASE_58_LENGTH,
          s"Invalid mc address length: ${mcTransparentAddress.length}")
        mcTransparentAddress.getBytes(StandardCharsets.UTF_8)
      case None =>
        new Array[Byte](BytesUtils.HORIZEN_MC_TRANSPARENT_ADDRESS_BASE_58_LENGTH)
    }

    val listOfParams: util.List[Type[_]] = util.Arrays.asList(
      new Bytes3(util.Arrays.copyOfRange(mcAddrBytes, 0, 3)),
      new Bytes32(util.Arrays.copyOfRange(mcAddrBytes, 3, BytesUtils.HORIZEN_MC_TRANSPARENT_ADDRESS_BASE_58_LENGTH))
    )
    new StaticStruct(listOfParams)
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

  override val getListOfABIParamTypes: util.List[TypeReference[Type[_]]] = {
    org.web3j.abi.Utils.convert(util.Arrays.asList(
      // mc transparent address
      new TypeReference[Bytes3]() {},
      new TypeReference[Bytes32]() {},
    ))
  }

  override def createType(listOfParams: util.List[Type[_]]): RemoveOwnershipCmdInput = {
    val mcTransparentAddress =
      decodeMcAddress(listOfParams.get(0).asInstanceOf[Bytes3], listOfParams.get(1).asInstanceOf[Bytes32])

    RemoveOwnershipCmdInput(Some(mcTransparentAddress))
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

@JsonView(Array(classOf[Views.Default]))
case class OwnerScAddress(scAddress: String)
  extends BytesSerializable  with ABIEncodable[StaticStruct] {

  override type M = OwnerScAddress

  override def serializer: SparkzSerializer[OwnerScAddress] = OwnerScAddressSerializer

  override def toString: String = "%s(scAddress: %s)"
    .format(this.getClass.toString, scAddress)

  override def asABIType(): StaticStruct = {

    val listOfParams: util.List[Type[_]] = util.Arrays.asList(
      new AbiAddress(scAddress))
    new StaticStruct(listOfParams)
  }
}

object OwnerScAddrListEncoder extends ABIListEncoder[OwnerScAddress, StaticStruct]{
  override def getAbiClass: Class[StaticStruct] = classOf[StaticStruct]
}

object OwnerScAddressSerializer extends SparkzSerializer[OwnerScAddress] {
  override def serialize(s: OwnerScAddress, w: Writer): Unit = {
    val scAddressBytes = s.scAddress.getBytes(StandardCharsets.UTF_8)
    w.putBytes(scAddressBytes)
  }

  override def parse(r: Reader): OwnerScAddress = {
    val scAddressBytes = r.getBytes(2*BytesUtils.HORIZEN_ADDRESS_HASH_LENGTH)

    OwnerScAddress(
      new String(scAddressBytes, StandardCharsets.UTF_8)
    )
  }
}

