package com.horizen.block

import com.google.common.primitives.Longs
import com.horizen.ScorexEncoding
import com.horizen.box.{Box, RegularBox}
import com.horizen.proposition.Proposition
import com.horizen.secret.PrivateKey25519
import com.horizen.transaction.{BoxTransaction, SidechainTransaction}
import scorex.core.block.Block
import scorex.core.ModifierTypeId
import scorex.util.ModifierId
import scorex.core.serialization.Serializer
import scorex.core.transaction.proof.Signature25519
import scorex.crypto.hash.Blake2b256
import scorex.core.{bytesToId, idToBytes}

import scala.util.Try

class SidechainBlock(
                      override val parentId: ModifierId,
                      override val timestamp: Block.Timestamp,
                      val mainchainHeaders : Seq[MainchainHeader],
                      override val transactions: Seq[BoxTransaction[Proposition, Box[Proposition]]],
                      ownerBox: RegularBox,
                      ownerSignature: Signature25519,
                    ) extends Block[BoxTransaction[Proposition, Box[Proposition]]] {

  override type M = SidechainBlock

  override lazy val serializer = SidechainBlockSerializer

  override lazy val version: Block.Version = 0: Byte

  override val modifierTypeId: ModifierTypeId = SidechainBlock.ModifierTypeId

  override lazy val id: ModifierId =
    bytesToId(Blake2b256(idToBytes(parentId) ++ Longs.toByteArray(timestamp))) // TO DO: update later.

  lazy val messageToSign: Array[Byte] = ??? // Bytes concatenation of all data except signature itself

  // TO DO: check box and signature, mainchain hashes and related data, merkle roots, transactions, etc.
  def semanticValidity(): Boolean = ???
}

object SidechainBlock extends ScorexEncoding {
  val MAX_BLOCK_SIZE = 2048 * 1024 //2048K
  val ModifierTypeId: ModifierTypeId = scorex.core.ModifierTypeId @@ 3.toByte

  def create(parentId: Block.BlockId,
             timestamp: Block.Timestamp,
             sidechainTransactions: Seq[SidechainTransaction[Proposition, Box[Proposition]]],
             sidechainId: Array[Byte],
             mainchainBlocks: Seq[MainchainBlock],
             ownerBox: RegularBox,
             ownerPrivateKey: PrivateKey25519
            ) : Try[SidechainBlock] = {
    // get MainchainBlock headers and MC2SCAggreagateTransactions
    // try to create unsigned SidechainBlock
    // sign block and create signed SidechainBlock
    // check semantic validity of SidechainBlock
    null
  }
}



object SidechainBlockSerializer extends Serializer[SidechainBlock] {
  override def toBytes(obj: SidechainBlock): Array[Byte] = ???

  override def parseBytes(bytes: Array[Byte]): Try[SidechainBlock] = ???
}