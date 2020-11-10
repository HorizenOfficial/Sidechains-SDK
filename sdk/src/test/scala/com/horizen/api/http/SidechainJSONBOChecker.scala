package com.horizen.api.http

import com.fasterxml.jackson.databind.JsonNode
import com.horizen.block.{MainchainBlockReference, MainchainBlockReferenceData, MainchainHeader, SidechainBlock}
import com.horizen.box.{Box, BoxUnlocker, NoncedBox}
import com.horizen.transaction.{BoxTransaction, MC2SCAggregatedTransaction}
import com.horizen.utils.{ByteArrayWrapper, BytesUtils}
import org.junit.Assert._
import scorex.util.ModifierId

import scala.collection.JavaConverters._
import scala.collection.mutable

// TODO: This checker is not sustainable
class SidechainJSONBOChecker {

  def assertsOnTransactionJson(tNode: JsonNode): Unit = {
    assertTrue(tNode.isObject)
    assertTrue(tNode.elements().asScala.length >= 6)
    assertTrue(tNode.get("fee").isNumber)
    assertTrue(tNode.get("timestamp").isNumber)
    assertTrue(tNode.get("id").isTextual)
    assertTrue(tNode.get("modifierTypeId").isNumber)
    assertTrue(tNode.get("unlockers").isArray)
    assertTrue(tNode.get("newBoxes").isArray)
    val unlockersJsonNode = tNode.get("unlockers").elements().asScala.toList
    val newBoxesJsonNode = tNode.get("newBoxes").elements().asScala.toList
    unlockersJsonNode.foreach(node => {
      assertTrue(node.get("closedBoxId").isTextual)
      assertTrue(node.get("boxKey").isObject)
      assertEquals(2, node.get("boxKey").elements().asScala.length)
      val sign = node.get("boxKey")
      assertEquals(2, sign.elements().asScala.length)
      assertTrue(sign.get("signature").isTextual)
    })
    newBoxesJsonNode.foreach(node => {
      assertTrue(node.elements().asScala.length >= 4)
      assertTrue(node.elements().asScala.length <= 5)
      assertTrue(node.get("typeId").isInt)
      assertTrue(node.get("proposition").isObject)
      assertTrue(node.get("value").isNumber)
      assertTrue(node.get("id").isTextual)
      assertEquals(1, node.get("proposition").elements().asScala.length)
      val publicKey = node.get("proposition")
      assertEquals(1, publicKey.elements().asScala.length)
      assertTrue(publicKey.get("publicKey").isTextual)
      if (node.elements().asScala.length > 4)
        assertTrue(node.get("nonce").isNumber)
    })
  }

  def assertsOnTransactionJson(json: JsonNode, transaction: BoxTransaction[_, _]): Unit = {
    assertTrue(json.elements().asScala.length >= 6)
    assertTrue(json.get("fee").isNumber)
    assertEquals(transaction.fee(), json.get("fee").asLong())
    assertTrue(json.get("timestamp").isNumber)
    assertEquals(transaction.timestamp(), json.get("timestamp").asLong())
    assertTrue(json.get("id").isTextual)
    assertEquals(BytesUtils.toHexString(scorex.util.idToBytes(ModifierId @@ transaction.id)), json.get("id").asText())
    assertTrue(json.get("modifierTypeId").isNumber)
    assertEquals(transaction.modifierTypeId.toInt, json.get("modifierTypeId").asInt())

    assertTrue(json.get("unlockers").isArray)
    val unlockersJsonNode = json.get("unlockers").elements().asScala.toList
    assertEquals(transaction.unlockers().size(), unlockersJsonNode.size)
    val unlockers = transaction.unlockers()
    for (i <- 0 until unlockers.size())
      assertsOnBoxUnlockerJson(unlockersJsonNode(i), unlockers.get(i))

    assertTrue(json.get("newBoxes").isArray)
    val newBoxesJsonNode = json.get("newBoxes").elements().asScala.toList
    assertEquals(transaction.newBoxes().size(), newBoxesJsonNode.size)
    val newBoxes = transaction.newBoxes()
    for (i <- 0 until newBoxes.size())
      assertsOnBoxJson(newBoxesJsonNode(i), newBoxes.get(i).asInstanceOf[Box[_]])
  }

  def assertsOnBoxUnlockerJson(json: JsonNode, boxUnlocker: BoxUnlocker[_]): Unit = {
    assertEquals(2, json.elements().asScala.length)
    assertTrue(json.get("closedBoxId").isTextual)
    assertEquals(BytesUtils.toHexString(boxUnlocker.closedBoxId()), json.get("closedBoxId").asText())
    assertTrue(json.get("boxKey").isObject)
    assertEquals(2, json.get("boxKey").elements().asScala.length)
    val sign = json.get("boxKey")
    assertEquals(2, sign.elements().asScala.length)
    assertTrue(sign.get("signature").isTextual)
  }

  def assertsOnBoxJson(json: JsonNode, box: Box[_]): Unit = {
    assertTrue(json.elements().asScala.length >= 4)
    assertTrue(json.elements().asScala.length <= 7)
    assertTrue(json.get("typeId").isInt)
    assertTrue(json.get("proposition").isObject)
    assertTrue(json.get("value").isNumber)
    assertTrue(json.get("id").isTextual)
    assertEquals(BytesUtils.toHexString(box.id()), json.get("id").asText())
    assertEquals(box.value(), json.get("value").asLong())
    assertEquals(box.boxTypeId().toInt, json.get("typeId").asInt())
    assertEquals(1, json.get("proposition").elements().asScala.length)
    val publicKey = json.get("proposition")
    assertEquals(1, publicKey.elements().asScala.length)
    assertTrue(publicKey.get("publicKey").isTextual)
    if (json.elements().asScala.length > 4) {
      assertTrue(json.get("nonce").isNumber)
      assertEquals(box.asInstanceOf[NoncedBox[_]].nonce(), json.get("nonce").asLong())
    }
  }

  def assertsOnBlockJson(json: JsonNode, block: SidechainBlock): Unit = {
    assertEquals(json.toString, 8, json.elements().asScala.length)
    assertTrue(json.get("header").isObject)
    assertTrue(json.get("mainchainBlockReferencesData").isArray)
    assertTrue(json.get("sidechainTransactions").isArray)
    assertTrue(json.get("mainchainHeaders").isArray)
    assertTrue(json.get("ommers").isArray)
    assertTrue(json.get("timestamp").isNumber)
    assertTrue(json.get("parentId").isTextual)
    assertTrue(json.get("id").isTextual)

    val headerJson: JsonNode = json.get("header")
    assertEquals(12, headerJson.elements().asScala.length)
    assertTrue(headerJson.get("version").isNumber)
    assertTrue(headerJson.get("parentId").isTextual)
    assertTrue(headerJson.get("timestamp").isNumber)
    assertTrue(headerJson.get("sidechainTransactionsMerkleRootHash").isTextual)
    assertTrue(headerJson.get("mainchainMerkleRootHash").isTextual)
    assertTrue(headerJson.get("ommersMerkleRootHash").isTextual)
    assertTrue(headerJson.get("ommersCumulativeScore").isNumber)
    assertTrue(headerJson.get("forgingStakeInfo").isObject)
    assertTrue(headerJson.get("vrfProof").isObject)
    assertTrue(headerJson.get("forgingStakeMerklePath").isTextual)
    assertTrue(headerJson.get("id").isTextual)
    assertTrue(headerJson.get("signature").isObject)

    assertEquals(BytesUtils.toHexString(scorex.util.idToBytes(block.parentId)), json.get("parentId").asText())
    assertEquals(BytesUtils.toHexString(scorex.util.idToBytes(block.id)), json.get("id").asText())
    assertEquals(block.timestamp.toLong, json.get("timestamp").asLong())

    val forgingStakeInfo = headerJson.get("forgingStakeInfo")
    val forgingStakeInfoElementNames = forgingStakeInfo.fieldNames().asScala.toSet
    assertEquals(3, forgingStakeInfoElementNames.size)

    val forgingStakeInfoExpectedElements = Set("blockSignPublicKey", "vrfPublicKey", "stakeAmount")
    assertEquals(forgingStakeInfoExpectedElements, forgingStakeInfoElementNames)



    val mainchainBlockReferencesDataJson = json.get("mainchainBlockReferencesData").elements().asScala.toList
    val sidechainTransactions = json.get("sidechainTransactions").elements().asScala.toList
    assertEquals(block.mainchainBlockReferencesData.size, mainchainBlockReferencesDataJson.size)
    assertEquals(block.sidechainTransactions.size, sidechainTransactions.size)

    val mainchainBlockReferencesData = block.mainchainBlockReferencesData
    for (i <- mainchainBlockReferencesData.indices)
      assertsOnMainchainDataJson(mainchainBlockReferencesDataJson(i), mainchainBlockReferencesData(i))

    val scTransaction = block.sidechainTransactions
    for (i <- scTransaction.indices)
      assertsOnTransactionJson(sidechainTransactions(i), scTransaction(i))
  }

  def assertsOnMainchainHeaderJson(json: JsonNode, header: MainchainHeader): Unit = {
    assertEquals(9, json.elements().asScala.length)
    assertTrue(json.get("mainchainHeaderBytes").isTextual)
    assertTrue(json.get("version").isNumber)
    assertTrue(json.get("hashPrevBlock").isTextual)
    assertTrue(json.get("hashMerkleRoot").isTextual)
    assertTrue(json.get("hashSCMerkleRootsMap").isTextual)
    assertTrue(json.get("time").isNumber)
    assertTrue(json.get("bits").isNumber)
    assertTrue(json.get("nonce").isTextual)
    assertTrue(json.get("solution").isTextual)

    assertEquals(BytesUtils.fromHexString(json.get("mainchainHeaderBytes").asText()), header.mainchainHeaderBytes)
    assertEquals(BytesUtils.fromHexString(json.get("hashPrevBlock").asText()), header.hashPrevBlock)
    assertEquals(BytesUtils.fromHexString(json.get("hashMerkleRoot").asText()), header.hashMerkleRoot)
    assertEquals(BytesUtils.fromHexString(json.get("hashScTxsCommitment").asText()), header.hashScTxsCommitment)
    assertEquals(BytesUtils.fromHexString(json.get("nonce").asText()), header.nonce)
    assertEquals(BytesUtils.fromHexString(json.get("solution").asText()), header.solution)

    assertEquals(json.get("bits").asInt(), header.bits)
    assertEquals(json.get("time").asInt(), header.time)
    assertEquals(json.get("version").asInt(), header.version)
  }


  def assertsOnMainchainDataJson(json: JsonNode, data: MainchainBlockReferenceData): Unit = {
    assertEquals(3, json.elements().asScala.length)
    assertTrue(json.get("headerHash").isTextual)
    assertTrue(json.get("sidechainRelatedAggregatedTransaction").isObject)
    assertTrue(json.get("merkleRoots").isArray)

    // TODO: check all json fields in MainchainBlockReferenceData
    //assertsOnMainchainHeaderJson(json.get("header"), mc.header)
    //assertsOnMerklerootsJson(json.get("merkleRoots"), mc.sidechainsMerkleRootsMap)
    //assertsOnM2SCTransactionJson(json.get("sidechainRelatedAggregatedTransaction"), mc.sidechainRelatedAggregatedTransaction.get)
    assertEquals(BytesUtils.fromHexString(json.get("headerHash").asText()), data.headerHash)
    assertsOnM2SCTransactionJson(json.get("sidechainRelatedAggregatedTransaction"), data.sidechainRelatedAggregatedTransaction)
  }

  def assertsOnM2SCTransactionJson(json: JsonNode, sdt: Option[MC2SCAggregatedTransaction]): Unit = {
    if (sdt.isDefined) {
      val m2sc: MC2SCAggregatedTransaction = sdt.get
      assertTrue(json.get("mc2scTransactionsMerkleRootHash").isTextual)
      assertTrue(json.get("timestamp").isNumber)
      assertsOnTransactionJson(json, m2sc)
    }
  }

  def assertsOnMerklerootsJson(json: JsonNode, mr: Option[mutable.Map[ByteArrayWrapper, Array[Byte]]]): Unit = {
    if (mr.isDefined) {
      val pairs = json.elements().asScala.toList
      val map: mutable.Map[ByteArrayWrapper, Array[Byte]] = mr.get
      assertEquals(map.size, pairs.size)
      pairs.foreach(node => {
        assertEquals(2, node.elements().asScala.length)
        assertTrue(node.get("key").isTextual)
        assertTrue(node.get("value").isTextual)
        val key = json.get("key").asText()
        val value = json.get("value").asText()
        val v = map.get(new ByteArrayWrapper(BytesUtils.fromHexString(key)))
        assertTrue(v.isDefined)
        assertEquals(BytesUtils.fromHexString(value), v)
      })
    }
  }
}
