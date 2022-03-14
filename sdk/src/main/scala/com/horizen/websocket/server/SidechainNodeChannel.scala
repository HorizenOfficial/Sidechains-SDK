package com.horizen.websocket.server

import com.fasterxml.jackson.databind.node.ObjectNode
import com.horizen.block.SidechainBlock

import scala.util.Try

trait SidechainNodeChannel {
  // Get Sidechain block by height
  def getBlockInfoByHeight(height: Int): Try[ObjectNode]

  // Get Sidechain block by hash
  def getBlockInfoByHash(hash: String): Try[ObjectNode]

  // For given locator find the best known block in SC active chain - common point.
  // Then return common point height and seq of block hashes up to `limit` elements starting from common point.
  def getNewBlockHashes(locatorHashes: Seq[String], limit: Int): Try[ObjectNode]

  // Get mempool transaction based and the hashes provided
  def getMempoolTxs(txids: Seq[String]): Try[ObjectNode]

  // Get the current hashes of transactions in mempool
  def getRawMempool(): Try[ObjectNode]

  // Get current best block json info
  def getBestBlockInfo(): Try[ObjectNode]

  // Get block json info
  def getBlockInfo(block: SidechainBlock): Try[ObjectNode]
}
