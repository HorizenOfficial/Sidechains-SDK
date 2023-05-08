package io.horizen.account.state

import com.fasterxml.jackson.annotation.JsonView
import io.horizen.account.abi.{ABIDecoder, ABIEncodable, MsgProcessorInputDecoder}
import io.horizen.account.proof.SignatureSecp256k1
import io.horizen.account.utils.{BigIntegerUInt256, Secp256k1}
import BigIntegerUInt256.getUnsignedByteArray
import io.horizen.account.state.McAddrOwnershipData.decodeMcAddress
import io.horizen.json.Views
import io.horizen.evm.Address
import io.horizen.utils.BytesUtils.padWithZeroBytes
import org.web3j.abi.TypeReference
import org.web3j.abi.datatypes.generated.{Bytes1, Bytes3, Bytes32}
import org.web3j.abi.datatypes.{StaticStruct, Type, Address => AbiAddress}
import sparkz.core.serialization.{BytesSerializable, SparkzSerializer}
import sparkz.util.serialization.{Reader, Writer}

import java.math.BigInteger
import java.nio.charset.StandardCharsets
import java.util


case class AddNewOwnershipCmdInput(scAddress: Address, mcTransparentAddress: String, mcSignature: SignatureSecp256k1)
  extends ABIEncodable[StaticStruct] {

  override def asABIType(): StaticStruct = {
    val t_v = getUnsignedByteArray(mcSignature.getV)
    val t_r = padWithZeroBytes(getUnsignedByteArray(mcSignature.getR), Secp256k1.SIGNATURE_RS_SIZE)
    val t_s = padWithZeroBytes(getUnsignedByteArray(mcSignature.getS), Secp256k1.SIGNATURE_RS_SIZE)

    val mcAddrBytes = mcTransparentAddress.getBytes(StandardCharsets.UTF_8)
    val listOfParams: util.List[Type[_]] = util.Arrays.asList(
      new AbiAddress(scAddress.toString),
      new Bytes3(util.Arrays.copyOfRange(mcAddrBytes, 0, 3)),
      new Bytes32(util.Arrays.copyOfRange(mcAddrBytes, 3, 35)),
      new Bytes1(t_v),
      new Bytes32(t_r),
      new Bytes32(t_s))
    new StaticStruct(listOfParams)
  }

  override def toString: String = "%s(scAddress: %s, mcAddress: %s, signature: %s)"
    .format(
      this.getClass.toString,
      scAddress.toString, mcTransparentAddress, mcSignature.toString)
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
      new TypeReference[Bytes1]() {},
      new TypeReference[Bytes32]() {},
      new TypeReference[Bytes32]() {}))
  }

  override def createType(listOfParams: util.List[Type[_]]): AddNewOwnershipCmdInput = {
    val scAddress = new Address(listOfParams.get(0).asInstanceOf[AbiAddress].toString)
    val mcTransparentAddrress = decodeMcAddress(listOfParams.get(1).asInstanceOf[Bytes3], listOfParams.get(2).asInstanceOf[Bytes32])
    val mcSignature = new SignatureSecp256k1(
      new BigInteger(1, listOfParams.get(3).asInstanceOf[Bytes1].getValue),
      new BigInteger(1, listOfParams.get(4).asInstanceOf[Bytes32].getValue),
      new BigInteger(1, listOfParams.get(5).asInstanceOf[Bytes32].getValue)
    )

    AddNewOwnershipCmdInput(scAddress, mcTransparentAddrress, mcSignature)
  }
}

case class RemoveOwnershipCmdInput(scAddress: Address, mcTransparentAddressOpt: Option[String])
  extends ABIEncodable[StaticStruct] {

  override def asABIType(): StaticStruct = {
    val mcAddrBytes = mcTransparentAddressOpt match {
      case Some(mcTransparentAddress) =>
        mcTransparentAddress.getBytes(StandardCharsets.UTF_8)
      case None =>
        new Array[Byte](35)
    }

    val listOfParams: util.List[Type[_]] = util.Arrays.asList(
      new AbiAddress(scAddress.toString),
      new Bytes3(util.Arrays.copyOfRange(mcAddrBytes, 0, 3)),
      new Bytes32(util.Arrays.copyOfRange(mcAddrBytes, 3, 35))
    )
    new StaticStruct(listOfParams)
  }

  override def toString: String = "%s(scAddress: %s, mcAddress: %s)"
    .format(
      this.getClass.toString,
      scAddress.toString, mcTransparentAddressOpt.getOrElse("undef"))
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
  extends BytesSerializable {

  override type M = McAddrOwnershipData

  override def serializer: SparkzSerializer[McAddrOwnershipData] = McAddrOwnershipDataSerializer

  override def toString: String = "%s(scAddress: %s, mcAddress: %s)"
    .format(this.getClass.toString, scAddress, mcTransparentAddress)


}

object McAddrOwnershipData {
  def decodeMcAddress(first3Bytes: Bytes3, last32Bytes: Bytes32): String = {
    new String(first3Bytes.getValue ++ last32Bytes.getValue, StandardCharsets.UTF_8)
  }
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
