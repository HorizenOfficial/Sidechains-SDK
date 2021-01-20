package com.horizen.websocket.server

import scala.util.Try

abstract class RequestType(val code:Int)

trait SidechainNodeChannel {
  // Send Sidechain block by height
  def sendBlockByHeight(height: Int, requestId: Int, answerType: Int): Try[Unit]

  // Send Sidechain block by hash
  def sendBlockByHash(hash: String, requestId: Int, answerType: Int): Try[Unit]

  // For given locator find the best known block in SC active chain - common point.
  // Then return common point height and seq of block hashes up to `limit` elements starting from common point.
  def sendNewBlockHashes(locatorHashes: Seq[String], limit: Int, requestId: Int, answerType: Int): Try[Unit]

  // Send mempool transaction based and the hashes provided
  def sendMempoolTxs(txids: Seq[String], requestId: Int, answerType: Int): Try[Unit]

  // Send the current hashes of transactions in mempool
  def sendRawMempool(requestId: Int, answerType:Int): Try[Unit]

  // Send current best block
  def sendBestBlock(): Try[Unit]

}
