package io.horizen.websocket.client
import io.horizen.block.{MainchainBlockReference, MainchainHeader}
import io.horizen.mainchain.api.MainchainNodeCertificateApi

import scala.util.Try

case class SidechainVersionsInfo(scId: String, version: Int)

case class OnUpdateTipEventPayload(height: Int, hash: String, block: String) extends EventPayload
trait OnUpdateTipEventHandler extends EventHandler[OnUpdateTipEventPayload]


trait MainchainNodeChannel extends MainchainNodeCertificateApi {
  // Get reference for given height in MC node active chain
  def getBlockByHeight(height: Int): Try[MainchainBlockReference]

  // Get reference for given hash in MC node block storage (any chain)
  def getBlockByHash(hash: String): Try[MainchainBlockReference]

  // Get up to `limit` block hashes from MC node active chain after given height.
  def getBlockHashesAfterHeight(height: Int, limit: Int): Try[Seq[String]]

  // Get up to `limit` block hashes from MC node active chain after given hash.
  def getBlockHashesAfterHash(hash: String, limit: Int): Try[Seq[String]]

  // For given locator find the best known block in MC active chain - common point.
  // Then return common point height and seq of block hashes up to `limit` elements starting from common point.
  def getNewBlockHashes(locatorHashes: Seq[String], limit: Int): Try[(Int, Seq[String])]

  // For given locator find the best known block in MC active chain - common point.
  // Return it's height and hash.
  def getBestCommonPoint(locatorHashes: Seq[String]): Try[(Int, String)]

  // Get block headers for given set of hashes in MC node block storage (any chain)
  def getBlockHeaders(hashes: Seq[String]): Try[Seq[MainchainHeader]]

  // Subscribe to receive block info of a new tip in MC node.
  def subscribeOnUpdateTipEvent(handler: OnUpdateTipEventHandler): Try[Unit]

  // Unsubscribe from new tip event.
  def unsubscribeOnUpdateTipEvent(handler: OnUpdateTipEventHandler): Unit

  def getSidechainVersions(scIds: Seq[String]): Try[Seq[SidechainVersionsInfo]]
}
