package com.horizen.block

import java.io.ByteArrayOutputStream
import java.time.Instant

import com.fasterxml.jackson.annotation.{JsonIgnoreProperties, JsonView}
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.google.common.primitives.{Bytes, Ints, Longs}
import com.horizen.{ScorexEncoding, SidechainTypes}
import com.horizen.box.{Box, NoncedBox}
import com.horizen.companion.SidechainTransactionsCompanion
import com.horizen.params.NetworkParams
import com.horizen.proof.{AbstractSignature25519, Signature25519}
import com.horizen.proposition.{Proposition, PublicKey25519Proposition}
import com.horizen.secret.PrivateKey25519
import com.horizen.serialization.{ScorexModifierIdSerializer, Views}
import com.horizen.transaction.SidechainTransaction
import com.horizen.utils.ListSerializer
import scorex.core.{ModifierTypeId, NodeViewModifier, bytesToId, idToBytes}
import scorex.core.block.Block
import scorex.core.serialization.ScorexSerializer
import scorex.crypto.hash.Blake2b256
import scorex.util.ModifierId
import scorex.util.serialization.{Reader, Writer}

import scala.collection.JavaConverters._
import scala.util.Try

@JsonView(Array(classOf[Views.Default]))
@JsonIgnoreProperties(Array("messageToSign", "transactions", "version", "serializer", "modifierTypeId", "encoder", "companion"))
class SidechainBlock (
                       @JsonSerialize(using = classOf[ScorexModifierIdSerializer]) override val parentId: ModifierId,
                       override val timestamp: Block.Timestamp,
                       val mainchainBlocks : Seq[MainchainBlockReference],
                       val sidechainTransactions: Seq[SidechainTransaction[Proposition, NoncedBox[Proposition]]],
                       val forgerPublicKey: PublicKey25519Proposition,
                       val signature: Signature25519,
                       companion: SidechainTransactionsCompanion)

  extends Block[SidechainTypes#SCBT]
{

  override type M = SidechainBlock

  override lazy val serializer = new SidechainBlockSerializer(companion)

  override lazy val version: Block.Version = 0: Byte

  override val modifierTypeId: ModifierTypeId = SidechainBlock.ModifierTypeId

  @JsonSerialize(using = classOf[ScorexModifierIdSerializer])
  override lazy val id: ModifierId =
    bytesToId(Blake2b256(Bytes.concat(messageToSign, signature.bytes)))

  override lazy val transactions: Seq[SidechainTypes#SCBT] = {
    var txs = Seq[SidechainTypes#SCBT]()

    for(b <- mainchainBlocks) {
      if (b.sidechainRelatedAggregatedTransaction.isDefined) {
        txs = txs :+ b.sidechainRelatedAggregatedTransaction.get
      }
    }
    for(tx <- sidechainTransactions)
      txs = txs :+ tx.asInstanceOf[SidechainTypes#SCBT]
    txs
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
      forgerPublicKey.bytes
    )
  }

  def semanticValidity(params: NetworkParams): Boolean = {
    if(parentId == null || parentId.length != 64
        || sidechainTransactions == null || sidechainTransactions.size > SidechainBlock.MAX_SIDECHAIN_TXS_NUMBER
        || mainchainBlocks == null || mainchainBlocks.size > SidechainBlock.MAX_MC_BLOCKS_NUMBER
        || forgerPublicKey == null || signature == null)
      return false

    // Check if timestamp is valid and not too far in the future
    if(timestamp <= 0 || timestamp > Instant.now.getEpochSecond + 2 * 60 * 60) // 2 * 60 * 60 like in Horizen MC
      return false

    // check Block size
    val blockSize: Int = bytes.length
    if(blockSize > SidechainBlock.MAX_BLOCK_SIZE)
      return false

    // check, that signature is valid
    if(!signature.isValid(forgerPublicKey, messageToSign))
      return false

    // Check MainchainBlockReferences order in current block
    for(i <- 1 until mainchainBlocks.size) {
      if(!mainchainBlocks(i).header.hashPrevBlock.sameElements(mainchainBlocks(i-1).hash))
        return false
    }

    // check MainchainBlockReferences validity
    for(b <- mainchainBlocks)
      if(!b.semanticValidity(params))
        return false

    true
  }

}


object SidechainBlock extends ScorexEncoding {
  val MAX_BLOCK_SIZE = 2048 * 1024 //2048K
  val MAX_MC_BLOCKS_NUMBER = 3
  val MAX_SIDECHAIN_TXS_NUMBER = 1000
  val ModifierTypeId: ModifierTypeId = scorex.core.ModifierTypeId @@ 3.toByte

  def create(parentId: Block.BlockId,
             timestamp: Block.Timestamp,
             mainchainBlocks : Seq[MainchainBlockReference],
             sidechainTransactions: Seq[SidechainTransaction[Proposition, NoncedBox[Proposition]]],
             ownerPrivateKey: PrivateKey25519,
             companion: SidechainTransactionsCompanion,
             params: NetworkParams,
             signatureOption: Option[Signature25519] = None // TO DO: later we should think about different unsigned/signed blocks creation methods
            ) : Try[SidechainBlock] = Try {
    require(parentId.length == 64)
    require(mainchainBlocks != null && mainchainBlocks.size <= SidechainBlock.MAX_MC_BLOCKS_NUMBER)
    require(sidechainTransactions != null)
    require(ownerPrivateKey != null)

    val signature = signatureOption match {
      case Some(signature) => signature
      case None =>
        val unsignedBlock: SidechainBlock = new SidechainBlock(
          parentId,
          timestamp,
          mainchainBlocks,
          sidechainTransactions,
          ownerPrivateKey.publicImage(),
          new Signature25519(new Array[Byte](Signature25519.SIGNATURE_LENGTH)), // empty signature
          companion
        )

        ownerPrivateKey.sign(unsignedBlock.messageToSign)
    }


    val block: SidechainBlock = new SidechainBlock(
      parentId,
      timestamp,
      mainchainBlocks,
      sidechainTransactions,
      ownerPrivateKey.publicImage(),
      signature,
      companion
    )

    if(!block.semanticValidity(params))
      throw new Exception("Sidechain Block is semantically invalid.")

    block
  }
}



class SidechainBlockSerializer(companion: SidechainTransactionsCompanion) extends ScorexSerializer[SidechainBlock] with SidechainTypes {
  private val _mcblocksSerializer: ListSerializer[MainchainBlockReference] = new ListSerializer[MainchainBlockReference](
    MainchainBlockReferenceSerializer,
    SidechainBlock.MAX_MC_BLOCKS_NUMBER
  )

  private val _sidechainTransactionsSerializer: ListSerializer[SidechainTypes#SCBT] = new ListSerializer[SidechainTypes#SCBT](
    companion,
    SidechainBlock.MAX_SIDECHAIN_TXS_NUMBER
  )

  override def serialize(obj: SidechainBlock, w: Writer): Unit = {
    w.putBytes(idToBytes(obj.parentId))
    w.putLong(obj.timestamp)

    val bw = w.newWriter()
    _mcblocksSerializer.serialize(obj.mainchainBlocks.asJava, bw)
    w.putInt(bw.length())
    w.append(bw)

    val tw = w.newWriter()
    _sidechainTransactionsSerializer.serialize(obj.sidechainTransactions.asJava, tw)
    w.putInt(tw.length())
    w.append(tw)

    w.putBytes(obj.forgerPublicKey.bytes())
    w.putBytes(obj.signature.bytes())
  }

  override def parse(r: Reader): SidechainBlock = {
    require(r.remaining <= SidechainBlock.MAX_BLOCK_SIZE)

    val parentId = bytesToId(r.getBytes(NodeViewModifier.ModifierIdSize))

    val timestamp = r.getLong()

    val mcbSize = r.getInt()

    if (r.remaining < mcbSize)
      throw new IllegalArgumentException("Input data corrupted.")

    val mcblocks: Seq[MainchainBlockReference] = _mcblocksSerializer.parse(r.newReader(r.getChunk(mcbSize))).asScala

    val txSize = r.getInt()

    if (r.remaining < txSize)
      throw new IllegalArgumentException("Input data corrupted.")

    val sidechainTransactions: Seq[SidechainTransaction[Proposition, NoncedBox[Proposition]]] =
      _sidechainTransactionsSerializer.parse(r.newReader(r.getChunk(txSize)))
        .asScala
        .map(t => t.asInstanceOf[SidechainTransaction[Proposition, NoncedBox[Proposition]]])

    val owner = new PublicKey25519Proposition(r.getBytes(PublicKey25519Proposition.KEY_LENGTH))

    val ownerSignature = new Signature25519(r.getBytes(Signature25519.SIGNATURE_LENGTH))

    new SidechainBlock(
      parentId,
      timestamp,
      mcblocks,
      sidechainTransactions,
      owner,
      ownerSignature,
      companion
    )
  }
}