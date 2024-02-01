package io.horizen.account.state

import com.fasterxml.jackson.annotation.JsonView
import io.horizen.account.abi.{ABIDecoder, ABIEncodable, ABIListEncoder, MsgProcessorInputDecoder}
import io.horizen.account.proof.SignatureSecp256k1
import io.horizen.account.proposition.{AddressProposition, AddressPropositionSerializer}
import io.horizen.account.utils.{BigIntegerUInt256, Secp256k1}
import BigIntegerUInt256.getUnsignedByteArray
import io.horizen.proof.Signature25519
import io.horizen.proposition.{PublicKey25519Proposition, PublicKey25519PropositionSerializer, VrfPublicKey, VrfPublicKeySerializer}
import io.horizen.json.Views
import io.horizen.utils.{BytesUtils, Ed25519}
import io.horizen.evm.Address
import io.horizen.utils.BytesUtils.padWithZeroBytes
import org.web3j.abi.TypeReference
import org.web3j.abi.datatypes.generated.{Bytes1, Bytes32, Uint256, Uint32}
import org.web3j.abi.datatypes.{StaticStruct, Type, Address => AbiAddress}
import sparkz.core.serialization.{BytesSerializable, SparkzSerializer}
import sparkz.util.serialization.{Reader, Writer}

import java.math.BigInteger
import java.util

@JsonView(Array(classOf[Views.Default]))
// used as element of the list to return when getting all forger stakes via msg processor
case class AccountForgingStakeInfo(
                                    stakeId: Array[Byte],
                                    forgerStakeData: ForgerStakeData)
  extends BytesSerializable with ABIEncodable[StaticStruct] {

  override type M = AccountForgingStakeInfo

  override def serializer: SparkzSerializer[AccountForgingStakeInfo] = AccountForgingStakeInfoSerializer

  override def toString: String = "%s(stakeId: %s, forgerStakeData: %s)"
    .format(this.getClass.toString, BytesUtils.toHexString(stakeId), forgerStakeData)

  private[horizen] def asABIType(): StaticStruct = {

    val forgerPublicKeysParams = forgerStakeData.forgerPublicKeys.asABIType().getValue.asInstanceOf[util.Collection[_ <: Type[_]]]
    val listOfParams = new util.ArrayList[Type[_]]()

    listOfParams.add(new Bytes32(stakeId))
    listOfParams.add(new Uint256(forgerStakeData.stakedAmount))
    listOfParams.add(new AbiAddress(forgerStakeData.ownerPublicKey.address().toString))

    listOfParams.addAll(forgerPublicKeysParams)

    new StaticStruct(listOfParams)
  }

  override def equals(that: Any): Boolean =
    that match {
      case that: AccountForgingStakeInfo =>
        that.canEqual(this) &&
          this.forgerStakeData == that.forgerStakeData &&
          util.Arrays.equals(this.stakeId, that.stakeId)
      case _ => false
    }


  override def hashCode: Int = {
    val prime = 31
    var result = 1
    result = prime * result + (if (stakeId == null) 0 else util.Arrays.hashCode(stakeId))
    result = prime * result + (if (forgerStakeData == null) 0 else forgerStakeData.hashCode)
    result
  }

}

object AccountForgingStakeInfoListEncoder extends ABIListEncoder[AccountForgingStakeInfo, StaticStruct]{
  override def getAbiClass: Class[StaticStruct] = classOf[StaticStruct]
}

object AccountForgingStakeInfoSerializer extends SparkzSerializer[AccountForgingStakeInfo] {

  override def serialize(s: AccountForgingStakeInfo, w: Writer): Unit = {
    w.putBytes(s.stakeId)
    ForgerStakeDataSerializer.serialize(s.forgerStakeData, w)
  }

  override def parse(r: Reader): AccountForgingStakeInfo = {
    val stakeId = r.getBytes(32)
    val forgerStakeData = ForgerStakeDataSerializer.parse(r)

    AccountForgingStakeInfo(stakeId, forgerStakeData)
  }
}

@JsonView(Array(classOf[Views.Default]))
case class ForgerPublicKeys(
                             blockSignPublicKey: PublicKey25519Proposition,
                             vrfPublicKey: VrfPublicKey)
  extends BytesSerializable with ABIEncodable[StaticStruct] {
  override type M = ForgerPublicKeys

  private[horizen] def vrfPublicKeyToAbi(vrfPublicKey: Array[Byte]): (Bytes32, Bytes1) = {
    val vrfPublicKeyFirst32Bytes = new Bytes32(util.Arrays.copyOfRange(vrfPublicKey, 0, 32))
    val vrfPublicKeyLastByte = new Bytes1(Array[Byte](vrfPublicKey(32)))
    (vrfPublicKeyFirst32Bytes, vrfPublicKeyLastByte)
  }

  override def asABIType(): StaticStruct = {

    val vrfPublicKeyBytes = vrfPublicKeyToAbi(vrfPublicKey.pubKeyBytes())

    new StaticStruct(
      new Bytes32(blockSignPublicKey.bytes()),
      vrfPublicKeyBytes._1,
      vrfPublicKeyBytes._2
    )
  }

  override def serializer: SparkzSerializer[ForgerPublicKeys] = ForgerPublicKeysSerializer

}

object ForgerPublicKeysSerializer extends SparkzSerializer[ForgerPublicKeys] {

  override def serialize(s: ForgerPublicKeys, w: Writer): Unit = {
    PublicKey25519PropositionSerializer.getSerializer.serialize(s.blockSignPublicKey, w)
    VrfPublicKeySerializer.getSerializer.serialize(s.vrfPublicKey, w)
  }

  override def parse(r: Reader): ForgerPublicKeys = {
    val blockSignProposition = PublicKey25519PropositionSerializer.getSerializer.parse(r)
    val vrfPublicKey = VrfPublicKeySerializer.getSerializer.parse(r)
    ForgerPublicKeys(blockSignProposition, vrfPublicKey)
  }
}


case class AddNewStakeCmdInput(
            forgerPublicKeys: ForgerPublicKeys,
            ownerAddress: Address) extends ABIEncodable[StaticStruct] {

  override def asABIType(): StaticStruct = {
    val forgerPublicKeysAbi = forgerPublicKeys.asABIType()
    val listOfParams: util.List[Type[_]] = new util.ArrayList(forgerPublicKeysAbi.getValue.asInstanceOf[util.List[Type[_]]])
    listOfParams.add(new AbiAddress(ownerAddress.toString))
    new StaticStruct(listOfParams)
  }

  override def toString: String = "%s(forgerPubKeys: %s, ownerAddress: %s)"
    .format(this.getClass.toString, forgerPublicKeys, ownerAddress)
}

object AddNewStakeCmdInputDecoder
  extends ABIDecoder[AddNewStakeCmdInput]
    with MsgProcessorInputDecoder[AddNewStakeCmdInput] {

  override val getListOfABIParamTypes: util.List[TypeReference[Type[_]]] =
    org.web3j.abi.Utils.convert(util.Arrays.asList(
      new TypeReference[Bytes32]() {},
      new TypeReference[Bytes32]() {},
      new TypeReference[Bytes1]() {},
      new TypeReference[AbiAddress]() {}))

   override def createType(listOfParams: util.List[Type[_]]): AddNewStakeCmdInput = {
    val forgerPublicKey = new PublicKey25519Proposition(listOfParams.get(0).asInstanceOf[Bytes32].getValue)
    val vrfKey = decodeVrfKey(listOfParams.get(1).asInstanceOf[Bytes32], listOfParams.get(2).asInstanceOf[Bytes1])
    val forgerPublicKeys = ForgerPublicKeys(forgerPublicKey, vrfKey)
    val ownerPublicKey = new Address(listOfParams.get(3).asInstanceOf[AbiAddress].toString)

    AddNewStakeCmdInput(forgerPublicKeys, ownerPublicKey)
  }

  private[horizen] def decodeVrfKey(vrfFirst32Bytes: Bytes32, vrfLastByte: Bytes1): VrfPublicKey = {
    val vrfinBytes = vrfFirst32Bytes.getValue ++ vrfLastByte.getValue
    new VrfPublicKey(vrfinBytes)
  }
}


case class RemoveStakeCmdInput(
                                stakeId: Array[Byte],
                                signature: SignatureSecp256k1)
  extends ABIEncodable[StaticStruct] {

  override def asABIType(): StaticStruct = {
    val t_v = getUnsignedByteArray(signature.getV)
    val t_r = padWithZeroBytes(getUnsignedByteArray(signature.getR), Secp256k1.SIGNATURE_RS_SIZE)
    val t_s = padWithZeroBytes(getUnsignedByteArray(signature.getS), Secp256k1.SIGNATURE_RS_SIZE)

    val listOfParams: util.List[Type[_]] = util.Arrays.asList(new Bytes32(stakeId), new Bytes1(t_v), new Bytes32(t_r), new Bytes32(t_s))
    new StaticStruct(listOfParams)
  }

  override def toString: String = "%s(stakeId: %s, signature: %s)"
    .format(this.getClass.toString, BytesUtils.toHexString(stakeId), signature)
}

object RemoveStakeCmdInputDecoder
  extends ABIDecoder[RemoveStakeCmdInput]
    with MsgProcessorInputDecoder [RemoveStakeCmdInput] {

  override val getListOfABIParamTypes: util.List[TypeReference[Type[_]]] =
    org.web3j.abi.Utils.convert(util.Arrays.asList(
      new TypeReference[Bytes32]() {},
      new TypeReference[Bytes1]() {},
      new TypeReference[Bytes32]() {},
      new TypeReference[Bytes32]() {}))

  override def createType(listOfParams: util.List[Type[_]]): RemoveStakeCmdInput = {
    val stakeId = listOfParams.get(0).asInstanceOf[Bytes32].getValue
    val signature = decodeSignature(listOfParams.get(1).asInstanceOf[Bytes1], listOfParams.get(2).asInstanceOf[Bytes32], listOfParams.get(3).asInstanceOf[Bytes32])

    RemoveStakeCmdInput(stakeId, signature)
  }

  private[horizen] def decodeSignature(v: Bytes1, r: Bytes32, s: Bytes32): SignatureSecp256k1 = {
    new SignatureSecp256k1(new BigInteger(1, v.getValue), new BigInteger(1, r.getValue), new BigInteger(1, s.getValue))
  }
}


case class OpenStakeForgerListCmdInput(
                                        forgerIndex: Int, signature: Signature25519) extends ABIEncodable[StaticStruct] {

  require(!(forgerIndex <0))

  override def asABIType(): StaticStruct = {
    val signatureBytes = signature.bytes
    new StaticStruct(
      new Uint32(forgerIndex),
      new Bytes32(util.Arrays.copyOfRange(signatureBytes, 0, 32)),
      new Bytes32(util.Arrays.copyOfRange(signatureBytes, 32, Ed25519.signatureLength()))
    )
  }

  override def toString: String = "%s(forgerIndex: %d, signature: %s)"
    .format(this.getClass.toString, forgerIndex, signature)

}

object OpenStakeForgerListCmdInputDecoder
  extends ABIDecoder[OpenStakeForgerListCmdInput]
    with MsgProcessorInputDecoder[OpenStakeForgerListCmdInput] {

  override val getListOfABIParamTypes: util.List[TypeReference[Type[_]]] =
    org.web3j.abi.Utils.convert(util.Arrays.asList(
      new TypeReference[Uint32]() {}, // forgerIndex, we use 4 bytes for big values, just in case
      new TypeReference[Bytes32]() {}, // first 32 bytes of signature
      new TypeReference[Bytes32]() {}  // second 32 bytes (signature is 64 in total)
    ))

  override def createType(listOfParams: util.List[Type[_]]): OpenStakeForgerListCmdInput = {
    val forgerIndex = listOfParams.get(0).asInstanceOf[Uint32].getValue
    val signature = decodeSignature(
      listOfParams.get(1).asInstanceOf[Bytes32],
      listOfParams.get(2).asInstanceOf[Bytes32])

    OpenStakeForgerListCmdInput(forgerIndex.intValueExact(), signature)
  }

  private[horizen] def decodeSignature(signaturePart1: Bytes32, signaturePart2: Bytes32): Signature25519 = {
    val totalBytes = signaturePart1.getValue ++ signaturePart2.getValue
    new Signature25519(totalBytes)
  }
}


trait ForgerStakeStorageElem {
  val forgerPublicKeys: ForgerPublicKeys
  val ownerPublicKey: AddressProposition
  val stakedAmount: BigInteger
}

// the forger stake data record, stored in stateDb as: key=stakeId / value=data
@JsonView(Array(classOf[Views.Default]))
case class ForgerStakeData(
                            override val forgerPublicKeys: ForgerPublicKeys,
                            override val ownerPublicKey: AddressProposition,
                            override val stakedAmount: BigInteger)
  extends BytesSerializable with ForgerStakeStorageElem {

  require(stakedAmount.signum() != -1, "stakeAmount expected to be non negative.")

  override type M = ForgerStakeData

  override def serializer: SparkzSerializer[ForgerStakeData] = ForgerStakeDataSerializer

  override def toString: String = "%s(forgerPubKeys: %s, ownerAddress: %s, stakedAmount: %s)"
    .format(this.getClass.toString, forgerPublicKeys, ownerPublicKey, stakedAmount)
}

object ForgerStakeDataSerializer extends SparkzSerializer[ForgerStakeData] {
  override def serialize(s: ForgerStakeData, w: Writer): Unit = {
    ForgerPublicKeysSerializer.serialize(s.forgerPublicKeys, w)
    AddressPropositionSerializer.getSerializer.serialize(s.ownerPublicKey, w)
    w.putInt(s.stakedAmount.toByteArray.length)
    w.putBytes(s.stakedAmount.toByteArray)
  }

  override def parse(r: Reader): ForgerStakeData = {
    val forgerPublicKeys = ForgerPublicKeysSerializer.parse(r)
    val ownerPublicKey = AddressPropositionSerializer.getSerializer.parse(r)

    val stakeAmountLength = r.getInt()
    val stakeAmount = new BigIntegerUInt256(r.getBytes(stakeAmountLength)).getBigInt

    ForgerStakeData(forgerPublicKeys, ownerPublicKey, stakeAmount)
  }
}

@JsonView(Array(classOf[Views.Default]))
case class ForgerStakeStorageElemV2(
                              override val forgerPublicKeys: ForgerPublicKeys,
                              override val ownerPublicKey: AddressProposition,
                              override val stakedAmount: BigInteger,
                              var stakeListIndex: Int,
                              var ownerListIndex: Int)
  extends BytesSerializable with ForgerStakeStorageElem {

  require(stakedAmount.signum() != -1, "stakeAmount expected to be non negative.")

  override type M = ForgerStakeStorageElemV2

  override def serializer: SparkzSerializer[ForgerStakeStorageElemV2] = ForgerStakeStorageElemV2Serializer

  override def toString: String = "%s(forgerPubKeys: %s, ownerAddress: %s, stakedAmount: %s, stakeListIndex: %s, ownerListIndex: %s)"
    .format(this.getClass.toString, forgerPublicKeys, ownerPublicKey, stakedAmount, stakeListIndex, ownerListIndex)
}

object ForgerStakeStorageElemV2Serializer extends SparkzSerializer[ForgerStakeStorageElemV2] {
  override def serialize(s: ForgerStakeStorageElemV2, w: Writer): Unit = {
    ForgerPublicKeysSerializer.serialize(s.forgerPublicKeys, w)
    AddressPropositionSerializer.getSerializer.serialize(s.ownerPublicKey, w)
    w.putInt(s.stakedAmount.toByteArray.length)
    w.putBytes(s.stakedAmount.toByteArray)
    w.putInt(s.stakeListIndex)
    w.putInt(s.ownerListIndex)
  }

  override def parse(r: Reader): ForgerStakeStorageElemV2 = {
    val forgerPublicKeys = ForgerPublicKeysSerializer.parse(r)
    val ownerPublicKey = AddressPropositionSerializer.getSerializer.parse(r)

    val stakeAmountLength = r.getInt()
    val stakeAmount = new BigIntegerUInt256(r.getBytes(stakeAmountLength)).getBigInt
    val stakeListIndex = r.getInt()
    val ownerListIndex = r.getInt()
    ForgerStakeStorageElemV2(forgerPublicKeys, ownerPublicKey, stakeAmount, stakeListIndex, ownerListIndex)
  }
}

case class ForgerStakeStorageIndexes(
                                     var stakeListIndex: Int,
                                     var ownerListIndex: Int)
  extends BytesSerializable {

  override type M = ForgerStakeStorageIndexes

  override def serializer: SparkzSerializer[ForgerStakeStorageIndexes] = ForgerStakeStorageIndexesSerializer

  override def toString: String = "%s(stakeListIndex: %s, ownerListIndex: %s)"
    .format(this.getClass.toString, stakeListIndex, ownerListIndex)
}


object ForgerStakeStorageIndexesSerializer extends SparkzSerializer[ForgerStakeStorageIndexes] {
  override def serialize(s: ForgerStakeStorageIndexes, w: Writer): Unit = {
    w.putInt(s.stakeListIndex)
    w.putInt(s.ownerListIndex)
  }

  override def parse(r: Reader): ForgerStakeStorageIndexes = {
    val stakeListIndex = r.getInt()
    val ownerListIndex = r.getInt()
    ForgerStakeStorageIndexes(stakeListIndex, ownerListIndex)
  }
}


case class ForgerStakesFilterByOwner(ownerAddress: Address) extends ABIEncodable[StaticStruct] {

  override def asABIType(): StaticStruct = {

    val listOfParams: util.List[Type[_]] = util.Arrays.asList(
      new AbiAddress(ownerAddress.toString)
    )
    new StaticStruct(listOfParams)
  }

  override def toString: String = "%s(ownerAddress: %s)"
    .format(
      this.getClass.toString,
      ownerAddress.toString)

}

object ForgerStakesFilterByOwnerDecoder
  extends ABIDecoder[ForgerStakesFilterByOwner]
  with MsgProcessorInputDecoder[ForgerStakesFilterByOwner] {

  override val getListOfABIParamTypes: util.List[TypeReference[Type[_]]] = {
    org.web3j.abi.Utils.convert(util.Arrays.asList(
      new TypeReference[AbiAddress]() {}
    ))
  }

  override def createType(listOfParams: util.List[Type[_]]): ForgerStakesFilterByOwner = {
    val address = new Address(listOfParams.get(0).asInstanceOf[AbiAddress].toString)
    ForgerStakesFilterByOwner(address)
  }
}

case class StakeAmount(totalStake: BigInteger) extends ABIEncodable[StaticStruct]{
  override def asABIType(): StaticStruct = {

    val listOfParams: util.List[Type[_]] = util.Arrays.asList(
      new Uint256(totalStake)
    )
    new StaticStruct(listOfParams)
  }

}
