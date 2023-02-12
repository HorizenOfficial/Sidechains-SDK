package com.horizen.account.block

import com.fasterxml.jackson.annotation.{JsonIgnoreProperties, JsonView}
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.google.common.primitives.{Bytes, Longs}
import com.horizen.account.proposition.{AddressProposition, AddressPropositionSerializer}
import com.horizen.account.receipt.{Bloom, BloomSerializer}
import com.horizen.account.utils.FeeUtils
import com.horizen.block.SidechainBlockHeaderBase
import com.horizen.consensus.{ForgingStakeInfo, ForgingStakeInfoSerializer}
import com.horizen.params.NetworkParams
import com.horizen.proof.{Signature25519, Signature25519Serializer, VrfProof, VrfProofSerializer}
import com.horizen.serialization.{MerklePathJsonSerializer, SparkzModifierIdSerializer, Views}
import com.horizen.utils.{Checker, MerklePath, MerklePathSerializer, MerkleTree}
import com.horizen.validation.InvalidSidechainBlockHeaderException
import org.bouncycastle.pqc.math.linearalgebra.ByteUtils
import sparkz.util.ModifierId
import sparkz.util.serialization.{Reader, Writer}
import sparkz.core.block.Block
import sparkz.core.serialization.{BytesSerializable, SparkzSerializer}
import sparkz.core.{NodeViewModifier, bytesToId, idToBytes}

import java.math.BigInteger
import scala.util.{Failure, Success, Try}

@JsonView(Array(classOf[Views.Default]))
@JsonIgnoreProperties(Array("messageToSign", "serializer"))
case class AccountBlockHeader(
                               override val version: Block.Version,
                               @JsonSerialize(using = classOf[SparkzModifierIdSerializer]) override val parentId: ModifierId,
                               override val timestamp: Block.Timestamp,
                               override val forgingStakeInfo: ForgingStakeInfo,
                               @JsonSerialize(using = classOf[MerklePathJsonSerializer]) override val forgingStakeMerklePath: MerklePath,
                               override val vrfProof: VrfProof,
                               override val sidechainTransactionsMerkleRootHash: Array[Byte], // don't need to care about MC2SCAggTxs here
                               override val mainchainMerkleRootHash: Array[Byte], // root hash of MainchainBlockReference.dataHash() root hash and MainchainHeaders root hash
                               stateRoot: Array[Byte],
                               receiptsRoot: Array[Byte],
                               forgerAddress: AddressProposition,
                               baseFee: BigInteger,
                               gasUsed: BigInteger,
                               gasLimit: BigInteger,
                               override val ommersMerkleRootHash: Array[Byte], // build on top of Ommer.id()
                               override val ommersCumulativeScore: Long, // to be able to calculate the score of the block without having the full SB. For future
                               override val feePaymentsHash: Array[Byte], // hash of the fee payments created during applying this block to the state. By default AccountFeePaymentsUtils.DEFAULT_ACCOUNT_FEE_PAYMENTS_HASH.
                               logsBloom: Bloom,
                               override val signature: Signature25519
                               ) extends SidechainBlockHeaderBase with BytesSerializable {

  override type M = AccountBlockHeader

  override def serializer: SparkzSerializer[AccountBlockHeader] = AccountBlockHeaderSerializer

  override lazy val messageToSign: Array[Byte] = {
    Bytes.concat(
      Array[Byte]{version},
      idToBytes(parentId),
      Longs.toByteArray(timestamp),
      forgingStakeInfo.hash,
      vrfProof.bytes, // TO DO: is it ok or define vrfProof.id() ?
      forgingStakeMerklePath.bytes(),
      sidechainTransactionsMerkleRootHash,
      mainchainMerkleRootHash,
      stateRoot,
      receiptsRoot,
      forgerAddress.bytes(),
      baseFee.toByteArray,
      gasUsed.toByteArray,
      gasLimit.toByteArray,
      ommersMerkleRootHash,
      Longs.toByteArray(ommersCumulativeScore),
      feePaymentsHash,
      logsBloom.getBytes
    )
  }

  override def semanticValidity(params: NetworkParams): Try[Unit] = Try {
    super.semanticValidity(params) match {
      case Success(_) =>

        if (stateRoot.length != MerkleTree.ROOT_HASH_LENGTH)
          throw new InvalidSidechainBlockHeaderException(s"AccountBlockHeader $id: invalid stateRoot.length ${stateRoot.length} != ${MerkleTree.ROOT_HASH_LENGTH}.")

        if (receiptsRoot.length != MerkleTree.ROOT_HASH_LENGTH)
          throw new InvalidSidechainBlockHeaderException(s"AccountBlockHeader $id: invalid receiptsRoot.length ${receiptsRoot.length} != ${MerkleTree.ROOT_HASH_LENGTH}.")

        if (version != AccountBlock.ACCOUNT_BLOCK_VERSION)
          throw new InvalidSidechainBlockHeaderException(s"AccountBlockHeader $id: version $version is invalid.")

        // check, that signature is valid
        if (!signature.isValid(forgingStakeInfo.blockSignPublicKey, messageToSign))
          throw new InvalidSidechainBlockHeaderException(s"AccountBlockHeader $id: signature is invalid.")

        // Check gasLimit and gasUsed are in the expected bound in their BigInteger representation
        // If the value of the BigInteger is out of the range of the long type, then an ArithmeticException is thrown.
        // --
        // this check is actually stricter than !BigIntegerUtil.isUint64(gas) since in java we can not handle unsigned
        // long values
        Try {
          gasLimit.longValueExact()
        } match {
          case Success(_) =>
          case Failure(exception) =>
            throw new InvalidSidechainBlockHeaderException(s"AccountBlockHeader $id: gasLimit size overflows. " + exception.getMessage)
        }
        Try {
          gasUsed.longValueExact()
        } match {
          case Success(_) =>
          case Failure(exception) =>
            throw new InvalidSidechainBlockHeaderException(s"AccountBlockHeader $id: gasUsed size overflows.. " + exception.getMessage)
        }

        if (baseFee.signum() < 1)
          throw new InvalidSidechainBlockHeaderException(s"AccountBlockHeader $id: baseFee=$baseFee is non positive and therefore invalid.")

        // check, that gas limit is valid
        if(gasLimit.compareTo(FeeUtils.GAS_LIMIT) != 0)
          throw new InvalidSidechainBlockHeaderException(s"AccountBlockHeader $id: gasLimit=$gasLimit is invalid.")
        if(gasUsed.signum() < 0)
          throw new InvalidSidechainBlockHeaderException(s"AccountBlockHeader $id: gasUsed=$gasUsed is below zero and therefore invalid.")
        if(gasUsed.compareTo(gasLimit) > 0)
          throw new InvalidSidechainBlockHeaderException(s"AccountBlockHeader $id: gasUsed=$gasUsed is greater than gasLimit=$gasLimit and therefore invalid.")


      case Failure(exception) =>
        throw exception
    }
  }


  override def toString: String =
    s"AccountBlockHeader($id, $version, $timestamp, $forgingStakeInfo, $vrfProof, " +
      s"${ByteUtils.toHexString(sidechainTransactionsMerkleRootHash)}, ${ByteUtils.toHexString(mainchainMerkleRootHash)}, " +
      s"${ByteUtils.toHexString(stateRoot)}, ${ByteUtils.toHexString(receiptsRoot)}, $forgerAddress" +
      s"$baseFee, $gasUsed, $gasLimit, " +
      s"${ByteUtils.toHexString(ommersMerkleRootHash)}, $ommersCumulativeScore, $signature)"
}


object AccountBlockHeaderSerializer extends SparkzSerializer[AccountBlockHeader] {
  override def serialize(obj: AccountBlockHeader, w: Writer): Unit = {
    w.put(obj.version)

    w.putBytes(idToBytes(obj.parentId))

    w.putLong(obj.timestamp)

    ForgingStakeInfoSerializer.serialize(obj.forgingStakeInfo, w)

    MerklePathSerializer.getSerializer.serialize(obj.forgingStakeMerklePath, w)

    VrfProofSerializer.getSerializer.serialize(obj.vrfProof, w)

    w.putBytes(obj.sidechainTransactionsMerkleRootHash)

    w.putBytes(obj.mainchainMerkleRootHash)

    w.putBytes(obj.stateRoot)

    w.putBytes(obj.receiptsRoot)

    AddressPropositionSerializer.getSerializer.serialize(obj.forgerAddress, w)

    val baseFee = obj.baseFee.toByteArray
    w.putInt(baseFee.length)
    w.putBytes(baseFee)

    val gasUsed = obj.gasUsed.toByteArray
    w.putInt(gasUsed.length)
    w.putBytes(gasUsed)

    val gasLimit = obj.gasLimit.toByteArray
    w.putInt(gasLimit.length)
    w.putBytes(gasLimit)

    w.putBytes(obj.ommersMerkleRootHash)

    w.putLong(obj.ommersCumulativeScore)

    w.putBytes(obj.feePaymentsHash)

    BloomSerializer.serialize(obj.logsBloom, w)

    Signature25519Serializer.getSerializer.serialize(obj.signature, w)
  }

  override def parse(reader: Reader): AccountBlockHeader = {
    val version: Block.Version = Checker.readByte(reader, "Block version")

    if(version != AccountBlock.ACCOUNT_BLOCK_VERSION)
      throw new InvalidSidechainBlockHeaderException(s"SidechainAccountBlock version $version is invalid.")

    val parentId: ModifierId = bytesToId(Checker.readBytes(reader, NodeViewModifier.ModifierIdSize, "parent id"))

    val timestamp: Block.Timestamp = Checker.readLongNotLessThanZero(reader, "timestamp")

    val forgingStakeInfo: ForgingStakeInfo = ForgingStakeInfoSerializer.parse(reader)

    val forgingStakeMerkle: MerklePath = MerklePathSerializer.getSerializer.parse(reader)

    val vrfProof: VrfProof = VrfProofSerializer.getSerializer.parse(reader)

    val sidechainTransactionsMerkleRootHash = Checker.readBytes(reader, MerkleTree.ROOT_HASH_LENGTH, "sidechain transactions merkle root hash")

    val mainchainMerkleRootHash = Checker.readBytes(reader, MerkleTree.ROOT_HASH_LENGTH, "mainchain merkle root hash")

    val stateRoot = Checker.readBytes(reader, MerkleTree.ROOT_HASH_LENGTH, "state root")

    val receiptsRoot = Checker.readBytes(reader, MerkleTree.ROOT_HASH_LENGTH, "receipts root")

    val forgerAddress = AddressPropositionSerializer.getSerializer.parse(reader)

    val baseFeeSize = Checker.readIntNotLessThanZero(reader, "base fee size")
    val baseFee = new BigInteger(Checker.readBytes(reader, baseFeeSize, "base fee"))

    val gasUsedSize = Checker.readIntNotLessThanZero(reader, "gas used size")
    val gasUsed = new BigInteger(Checker.readBytes(reader, gasUsedSize, "gas used"))

    val gasLimitSize = Checker.readIntNotLessThanZero(reader, "gas limit size")
    val gasLimit = new BigInteger(Checker.readBytes(reader, gasLimitSize, "gas limit"))

    val ommersMerkleRootHash = Checker.readBytes(reader, MerkleTree.ROOT_HASH_LENGTH, "ommers merkle root hash")

    val ommersCumulativeScore: Long = Checker.readLongNotLessThanZero(reader, "ommers cumulative score")

    val feePaymentsHash: Array[Byte] = Checker.readBytes(reader, NodeViewModifier.ModifierIdSize, "fee payments hash")

    val logsBloom: Bloom = BloomSerializer.parse(reader)

    val signature: Signature25519 = Signature25519Serializer.getSerializer.parse(reader)
    Checker.bufferShouldBeEmpty(reader.remaining)

    AccountBlockHeader(
      version,
      parentId,
      timestamp,
      forgingStakeInfo,
      forgingStakeMerkle,
      vrfProof,
      sidechainTransactionsMerkleRootHash,
      mainchainMerkleRootHash,
      stateRoot,
      receiptsRoot,
      forgerAddress,
      baseFee,
      gasUsed,
      gasLimit,
      ommersMerkleRootHash,
      ommersCumulativeScore,
      feePaymentsHash,
      logsBloom,
      signature)
  }
}
