package com.horizen.websocket

import com.horizen.block.MainchainBlockReference
import scala.util.Try


case class OnUpdateTipEventPayload(height: Int, hash: String, block: String) extends EventPayload
trait OnUpdateTipEventHandler extends EventHandler[OnUpdateTipEventPayload]


trait MainchainNodeChannel {
  def getBlockByHeight(height: Int): Try[MainchainBlockReference]

  def getBlockByHash(hash: String): Try[MainchainBlockReference]

  def getNewBlockHashes(locatorHashes: Seq[String], limit: Int): Try[Seq[String]]

  def subscribeOnUpdateTipEvent(handler: OnUpdateTipEventHandler): Try[Unit]

  def unsubscribeOnUpdateTipEvent(handler: OnUpdateTipEventHandler): Unit
}
