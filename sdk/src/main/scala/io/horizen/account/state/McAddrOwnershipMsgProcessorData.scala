package io.horizen.account.state

import com.fasterxml.jackson.annotation.JsonView
import io.horizen.account.abi.{ABIDecoder, ABIEncodable, ABIListEncoder, MsgProcessorInputDecoder}
import io.horizen.account.proof.SignatureSecp256k1
import io.horizen.account.utils.{BigIntegerUInt256, Secp256k1}
import BigIntegerUInt256.getUnsignedByteArray
import io.horizen.json.Views
import io.horizen.utils.BytesUtils
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

@JsonView(Array(classOf[Views.Default]))
// used as element of the list to return when getting all forger stakes via msg processor
case class McAddrOwnershipInfo(
                                ownershipId: Array[Byte],
                                mcAddrOwnershipData: McAddrOwnershipData)
  extends BytesSerializable with ABIEncodable[StaticStruct] {

  override type M = McAddrOwnershipInfo

  override def serializer: SparkzSerializer[McAddrOwnershipInfo] = McAddrOwnershipInfoSerializer

  override def toString: String = "%s(ownershipId: %s, McAddrOwnershipData: %s)"
    .format(this.getClass.toString, BytesUtils.toHexString(ownershipId), mcAddrOwnershipData)

  private[horizen] def asABIType(): StaticStruct = {
    val scAddrBytes = mcAddrOwnershipData.scAddress.getBytes(StandardCharsets.UTF_8)
    require(scAddrBytes.length == 20, "Sc address array should have length 20")

    val mcAddrBytes = mcAddrOwnershipData.mcTransparentAddress.getBytes(StandardCharsets.UTF_8)
    require(mcAddrBytes.length == 35, "Mc address array should have length 35")

    val listOfParams = new util.ArrayList[Type[_]]()

    listOfParams.add(new Bytes32(ownershipId))
    listOfParams.add(new Bytes3(util.Arrays.copyOfRange(mcAddrBytes, 0, 3)))
    listOfParams.add(new Bytes32(util.Arrays.copyOfRange(mcAddrBytes, 3, 35)))
    listOfParams.add(new AbiAddress(mcAddrOwnershipData.scAddress))

    new StaticStruct(listOfParams)
  }

  override def equals(that: Any): Boolean =
    that match {
      case that: McAddrOwnershipInfo =>
        that.canEqual(this) &&
          this.mcAddrOwnershipData == that.mcAddrOwnershipData &&
          util.Arrays.equals(this.ownershipId, that.ownershipId)
      case _ => false
    }


  override def hashCode: Int = {
    val prime = 31
    var result = 1
    result = prime * result + (if (ownershipId == null) 0 else util.Arrays.hashCode(ownershipId))
    result = prime * result + (if (mcAddrOwnershipData == null) 0 else mcAddrOwnershipData.hashCode)
    result
  }

}

object McAddrOwnershipInfoListEncoder extends ABIListEncoder[McAddrOwnershipInfo, StaticStruct]{
  override def getAbiClass: Class[StaticStruct] = classOf[StaticStruct]
}

object McAddrOwnershipInfoSerializer extends SparkzSerializer[McAddrOwnershipInfo] {

  override def serialize(s: McAddrOwnershipInfo, w: Writer): Unit = {
    w.putBytes(s.ownershipId)
    McAddrOwnershipDataSerializer.serialize(s.mcAddrOwnershipData, w)
  }

  override def parse(r: Reader): McAddrOwnershipInfo = {
    val ownershipId = r.getBytes(32)
    val McAddrOwnershipData = McAddrOwnershipDataSerializer.parse(r)

    McAddrOwnershipInfo(ownershipId, McAddrOwnershipData)
  }
}

case class AddNewOwnershipCmdInput(scAddress: Address, mcTransparentAddress: String, mcSignature: SignatureSecp256k1) extends ABIEncodable[StaticStruct] {

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

  override val getListOfABIParamTypes: util.List[TypeReference[Type[_]]] =
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

  private[horizen] def decodeMcAddress(first3Bytes: Bytes3, last32Bytes: Bytes32): String = {
    new String(first3Bytes.getValue ++ last32Bytes.getValue, StandardCharsets.UTF_8)
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
