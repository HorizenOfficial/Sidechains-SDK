package com.horizen.websocket

trait UpdateEventHandler[T] {
  def onEvent(obj: T)
}

case class MainchainTipInfo(height: Int, hash: String, block: String)

trait UpdateTipEventHandler extends UpdateEventHandler[MainchainTipInfo] {
  override def onEvent(obj: MainchainTipInfo)
}

