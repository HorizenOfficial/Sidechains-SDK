package com.horizen.datagenerator

import java.nio.ByteBuffer

import com.horizen.block.{MainchainBlockReference, MainchainBlockReferenceSerializer, SidechainBlock}
import com.horizen.box.ForgerBox
import com.horizen.fixtures.{CompanionsFixture, ForgerBoxFixture, MerkleTreeFixture}
import com.horizen.proof.Signature25519
import com.horizen.utils.ListSerializer
import com.horizen.vrf.{VRFKeyGenerator, VRFProof}
import org.junit.Test
import scorex.util.serialization.VLQByteBufferReader
import scorex.util.{ModifierId, bytesToId}

import scala.collection.JavaConverters._

class sc_node_holder_fixter_settings extends CompanionsFixture {
  private val mcBlocksSerializer: ListSerializer[MainchainBlockReference] = new ListSerializer[MainchainBlockReference](
    MainchainBlockReferenceSerializer,
    SidechainBlock.MAX_MC_BLOCKS_NUMBER
  )
  private val seed = 49850L

  @Test
  def generate_scGenesisBlockHex(): Unit = {
    val parentId: ModifierId = bytesToId(new Array[Byte](32))
    val timestamp = 1574077098L
    val mcBlockBytes: Array[Byte] = Array[Byte](2,-102,9,-30,2,0,0,0,32,70,-118,-119,28,-34,114,-6,73,-76,-79,-100,-63,-35,-82,-98,97,-126,-24,-71,64,-18,69,36,-124,70,-25,-40,-41,43,26,-47,13,4,15,-48,54,-76,57,4,86,-71,49,-76,-5,95,-19,-95,-119,85,-48,-8,-34,88,61,-25,73,78,4,-107,-106,-120,43,74,107,73,56,59,77,-28,23,-101,-43,-77,48,-112,27,67,-16,-43,-8,-84,77,65,3,-22,-56,-105,-73,-16,60,108,100,71,11,2,108,-92,-126,-46,93,3,15,15,32,8,0,-99,105,-97,-19,-67,84,27,-50,35,115,115,-94,84,115,11,-34,67,41,81,41,97,-88,-7,-32,125,91,93,-117,0,0,36,16,36,-8,-3,81,-117,-47,101,-83,50,97,-71,62,102,118,54,-17,-72,30,87,11,-112,4,-60,37,107,-51,69,-33,-91,-3,53,30,1,97,-66,-84,5,-97,68,-90,108,94,-1,18,82,-126,121,-115,-20,-38,-37,42,12,-75,-97,99,117,124,28,-68,-74,56,45,-78,-73,-28,125,105,9,0,0,0,0,93,-46,-126,-92,0,0,1,42,6,-110,1,-38,1,-38,1,3,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,-24,3,0,0,114,-75,81,78,20,-84,72,3,32,5,-38,-52,61,-49,54,-36,59,26,106,-13,-108,42,-2,-74,72,76,-75,78,110,21,-30,65,0,0,0,0,1,0,116,59,-92,11,0,0,0,-65,-56,-17,42,-3,-63,-57,-125,-128,68,-30,122,-122,-95,-26,-58,115,18,16,-14,-34,-64,54,70,63,-85,-55,-98,-67,-101,-123,-75,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,114,-75,81,78,20,-84,72,3,32,5,-38,-52,61,-49,54,-36,59,26,106,-13,-108,42,-2,-74,72,76,-75,78,110,21,-30,65,0,0,0,1,1,0,116,59,-92,11,0,0,0,-107,118,-102,-74,100,-90,81,117,68,119,-34,87,-37,-123,-56,46,-28,-63,74,-84,-92,-122,-114,-122,53,-38,-117,-3,-87,-62,23,-111,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,114,-75,81,78,20,-84,72,3,32,5,-38,-52,61,-49,54,-36,59,26,106,-13,-108,42,-2,-74,72,76,-75,78,110,21,-30,65,0,0,0,2,-128,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,-97,68,-90,108,94,-1,18,82,-126,121,-115,-20,-38,-37,42,12,-75,-97,99,117,124,28,-68,-74,56,45,-78,-73,-28,125,105,9)
    val reader: VLQByteBufferReader = new VLQByteBufferReader(ByteBuffer.wrap(mcBlockBytes))
    val mainchainReferences = mcBlocksSerializer.parse(reader)
    val (forgerBox: ForgerBox, forgerMetadata)= ForgerBoxFixture.generateForgerBox(seed)
    val (secretKey, publicKey) = VRFKeyGenerator.generate(seed.toString.getBytes)
    val vrfMessage: Array[Byte] = "!SomeVrfMessage1!SomeVrfMessage2".getBytes
    val vrfProof: VRFProof = secretKey.prove(vrfMessage)
    val merklePath = MerkleTreeFixture.generateRandomMerklePath(seed + 1)
    val companion = getDefaultTransactionsCompanion

    val unsignedBlock: SidechainBlock = new SidechainBlock(
      parentId,
      timestamp,
      mainchainReferences.asScala,
      Seq(),
      forgerBox,
      vrfProof,
      merklePath,
      new Signature25519(new Array[Byte](Signature25519.SIGNATURE_LENGTH)), // empty signature
      companion
    )

    val signature = forgerMetadata.rewardSecret.sign(unsignedBlock.messageToSign)

    val data = new SidechainBlock(parentId, timestamp, mainchainReferences.asScala, Seq(), forgerBox,
      vrfProof, merklePath, signature, companion)

    val hexString = data.bytes.map("%02X" format _).mkString.toLowerCase
    println(hexString)
  }
}
