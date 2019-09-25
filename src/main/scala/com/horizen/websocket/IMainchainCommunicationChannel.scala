package com.horizen.websocket

import com.horizen.block.MainchainBlockReference

import scala.util.Try

trait IMainchainCommunicationChannel {

  def getBlockByHeight(height: Int): Try[MainchainBlockReference]

  def getBlockByHash(hash: String): Try[MainchainBlockReference]

  def getBlockHashesAfterHeight(length: Int, afterHeight: Int): Try[Seq[String]]

  def getBlockHashesAfterHash(length: Int, afterHash: String): Try[Seq[String]]

  def sync(hashes: Seq[String], length: Int): Try[Seq[String]]

  // returns nothing or exception if some error occurred.
  def subscribeOnUpdateTipEvent(handler: UpdateTipEventHandler): Try[Unit]

  def unsubscribeOnUpdateTipEvent(handler: UpdateTipEventHandler)
}
