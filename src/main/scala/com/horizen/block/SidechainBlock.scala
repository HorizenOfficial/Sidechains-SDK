package com.horizen.block

import java.io.ByteArrayOutputStream
import java.time.Instant
import java.util

import com.google.common.primitives.{Bytes, Ints, Longs}
import com.horizen.ScorexEncoding
import com.horizen.box.Box
import com.horizen.params.NetworkParams
import com.horizen.proof.Signature25519
import com.horizen.proposition.{Proposition, PublicKey25519Proposition}
import com.horizen.secret.PrivateKey25519
import com.horizen.transaction.{BoxTransaction, SidechainTransaction}
import com.horizen.utils.{BytesUtils, ListSerializer}
import scorex.core.block.Block
import scorex.core.ModifierTypeId
import scorex.util.ModifierId
import scorex.core.serialization.Serializer
import scorex.crypto.hash.Blake2b256
import scorex.core.{bytesToId, idToBytes}

import scala.collection.JavaConverters._
import scala.util.{Success, Try}

class SidechainBlock (
                      override val parentId: ModifierId,
                      override val timestamp: Block.Timestamp,
                      val mainchainBlocks : Seq[MainchainBlock],
                      val sidechainTransactions: Seq[SidechainTransaction[Proposition, Box[Proposition]]],
                      val owner: PublicKey25519Proposition,
                      val ownerSignature: Signature25519,
                    ) extends Block[BoxTransaction[Proposition, Box[Proposition]]] {

  override type M = SidechainBlock

  override lazy val serializer = SidechainBlockSerializer

  override lazy val version: Block.Version = 0: Byte

  override val modifierTypeId: ModifierTypeId = SidechainBlock.ModifierTypeId

  override lazy val id: ModifierId =
    bytesToId(Blake2b256(Bytes.concat(messageToSign, ownerSignature.bytes)))

  override lazy val transactions: Seq[BoxTransaction[Proposition, Box[Proposition]]] = {
    val txs = Seq[BoxTransaction[Proposition, Box[Proposition]]]()

    for(b <- mainchainBlocks) {
      if (b.sidechainRelatedAggregatedTransaction.isDefined) {
        txs :+ b.sidechainRelatedAggregatedTransaction.get
      }
    }
    txs ++ sidechainTransactions
  }

  lazy val messageToSign: Array[Byte] = {
    val sidechainTransactionsStream = new ByteArrayOutputStream
    sidechainTransactions.foreach {
      tx => sidechainTransactionsStream.write(tx.bytes)
    }

    val mainchainBlocksStream = new ByteArrayOutputStream
    mainchainBlocks.foreach {
      mcblock => mainchainBlocksStream.write(mcblock.bytes)
    }

    Bytes.concat(
      idToBytes(parentId),
      Longs.toByteArray(timestamp),
      sidechainTransactionsStream.toByteArray,
      mainchainBlocksStream.toByteArray,
      owner.bytes
    )
  }

  def semanticValidity(params: NetworkParams): Boolean = {
    if(parentId == null || parentId.length != 32
        || sidechainTransactions == null
        || mainchainBlocks == null || mainchainBlocks.size > SidechainBlock.MAX_MC_BLOCKS_NUMBER
        || owner == null || ownerSignature == null)
      return false

    // Check if timestamp is valid and not too far in the future
    if(timestamp <= 0 || timestamp > Instant.now.getEpochSecond + 2 * 60 * 60) // 2* 60 * 60 like in Horizen MC
      return false

    // check Block size
    val blockSize: Int = bytes.length
    if(blockSize > SidechainBlock.MAX_BLOCK_SIZE)
      return false

    // check blocks and txs validity
    for(b <- mainchainBlocks)
      if(!b.semanticValidity(params))
        return false
    for(tx <- sidechainTransactions)
      if(!tx.semanticValidity())
        return false

    // check, that signature is valid
    if(!ownerSignature.isValid(owner, messageToSign))
      return false

    true
  }
}


object SidechainBlock extends ScorexEncoding {
  val MAX_BLOCK_SIZE = 2048 * 1024 //2048K
  val MAX_MC_BLOCKS_NUMBER = 3
  val ModifierTypeId: ModifierTypeId = scorex.core.ModifierTypeId @@ 3.toByte

  def create(parentId: Block.BlockId,
             timestamp: Block.Timestamp,
             mainchainBlocks : Seq[MainchainBlock],
             sidechainTransactions: Seq[SidechainTransaction[Proposition, Box[Proposition]]],
             ownerPrivateKey: PrivateKey25519,
             params: NetworkParams
            ) : Try[SidechainBlock] = {
    require(parentId.length == 32)
    require(mainchainBlocks != null && mainchainBlocks.size <= SidechainBlock.MAX_MC_BLOCKS_NUMBER)
    require(sidechainTransactions != null)
    require(ownerPrivateKey != null)

    val unsignedBlock: SidechainBlock = new SidechainBlock(
      parentId,
      timestamp,
      mainchainBlocks,
      sidechainTransactions,
      ownerPrivateKey.publicImage(),
      new Signature25519(new Array[Byte](Signature25519.SIGNATURE_LENGTH)) // empty signature
    )

    val signature = ownerPrivateKey.sign(unsignedBlock.messageToSign)

    val block: SidechainBlock = new SidechainBlock(
      parentId,
      timestamp,
      mainchainBlocks,
      sidechainTransactions,
      ownerPrivateKey.publicImage(),
      signature
    )

    if(!block.semanticValidity(params))
      throw new Exception("Sidechain Block is semantically invalid.")

    Success(block)
  }
}



object SidechainBlockSerializer extends Serializer[SidechainBlock] {
  private val _mcblocksSerializer: ListSerializer[MainchainBlock] = new ListSerializer[MainchainBlock](
    new util.HashMap[Integer, Serializer[MainchainBlock]]() {
      put(1, MainchainBlockSerializer)
    },
    SidechainBlock.MAX_BLOCK_SIZE)

  override def toBytes(obj: SidechainBlock): Array[Byte] = {
    val mcblocksBytes = _mcblocksSerializer.toBytes(obj.mainchainBlocks.toList.asJava)

    // TO DO: we should use SidechainTransactionsCompanion with all defined custom transactions
//    val sidechainTransactionsStream = new ByteArrayOutputStream
//    obj.sidechainTransactions.foreach {
//      tx => sidechainTransactionsStream.write(tx.bytes)
//    }

    Bytes.concat(
      idToBytes(obj.parentId),                  // 32 bytes
      Longs.toByteArray(obj.timestamp),         // 8 bytes
      Ints.toByteArray(mcblocksBytes.length),   // 4 bytes
      mcblocksBytes,                            // total size of all MC Blocks
//      sidechainTransactionsStream.toByteArray,  // ?
      obj.owner.bytes,                          // 32 bytes
      obj.ownerSignature.bytes                  // 64 bytes
    )
  }

  override def parseBytes(bytes: Array[Byte]): Try[SidechainBlock] = Try {
    require(bytes.length <= SidechainBlock.MAX_BLOCK_SIZE)
    require(bytes.length > 32 + 8 + 4 + 4 + 32 + 64) // size of empty block

    var offset: Int = 0

    val parentId = bytesToId(bytes.slice(offset, offset + 32))
    offset += 32

    val timestamp = BytesUtils.getLong(bytes, offset)
    offset += 8

    val mcblocksSize = BytesUtils.getInt(bytes, offset)
    offset += 4

    val mcblocks: Seq[MainchainBlock] = _mcblocksSerializer.parseBytes(bytes.slice(offset, offset + mcblocksSize)).get.asScala.toSeq
    offset += mcblocksSize

    // to do: parse SC txs

    val owner = new PublicKey25519Proposition(bytes.slice(offset, offset + PublicKey25519Proposition.KEY_LENGTH))
    offset += PublicKey25519Proposition.KEY_LENGTH

    val ownerSignature = new Signature25519(bytes.slice(offset, offset + Signature25519.SIGNATURE_LENGTH))
    offset += Signature25519.SIGNATURE_LENGTH

    require(offset == bytes.length)

    new SidechainBlock(
      parentId,
      timestamp,
      mcblocks,
      Seq[SidechainTransaction[Proposition, Box[Proposition]]](), // To Do: replace with real one
      owner,
      ownerSignature
    )

  }
}