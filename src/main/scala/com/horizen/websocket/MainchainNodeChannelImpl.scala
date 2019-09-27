package com.horizen.websocket
import com.horizen.block.{MainchainBlockReference, MainchainBlockReferenceSerializer}
import com.horizen.params.NetworkParams
import com.horizen.utils.BytesUtils

import scala.util.Try
import scala.concurrent.{Await, Future}


case class GetBlockByHeightRequestPayload(height: Int) extends RequestPayload
case class GetBlockByHashRequestPayload(hash: String) extends RequestPayload
case class GetNewBlocksRequestPayload(locatorHashes: Seq[String], limit: Int) extends RequestPayload


case class BlockResponsePayload(height: Int, hash: String, block: String) extends ResponsePayload
case class NewBlocksResponsePayload(height: Int, hashes: Seq[String]) extends ResponsePayload


class MainchainNodeChannelImpl(client: CommunicationClient, params: NetworkParams) extends MainchainNodeChannel { // to do: define EC inside?

  override def getBlockByHeight(height: Int): Try[MainchainBlockReference] = Try {
    val future: Future[BlockResponsePayload] =
      client.sendRequest(2, GetBlockByHeightRequestPayload(height), classOf[BlockResponsePayload])

    processBlockResponsePayload(future).get
  }

  override def getBlockByHash(hash: String): Try[MainchainBlockReference] = Try {
    val future: Future[BlockResponsePayload] =
      client.sendRequest(2, GetBlockByHashRequestPayload(hash), classOf[BlockResponsePayload])

    processBlockResponsePayload(future).get
  }

  private def processBlockResponsePayload(future: Future[BlockResponsePayload]): Try[MainchainBlockReference] = Try {
    val response: BlockResponsePayload = Await.result(future, client.requestTimeoutDuration())
    val blockBytes = BytesUtils.fromHexString(response.block)
    MainchainBlockReference.create(blockBytes, params).get
  }

  override def getNewBlockHashes(locatorHashes: Seq[String], limit: Int): Try[Seq[String]] = Try {
    val future: Future[NewBlocksResponsePayload] =
      client.sendRequest(5, GetNewBlocksRequestPayload(locatorHashes, limit), classOf[NewBlocksResponsePayload])

    val response: NewBlocksResponsePayload = Await.result(future, client.requestTimeoutDuration())
    response.hashes
  }

  override def subscribeOnUpdateTipEvent(handler: OnUpdateTipEventHandler): Try[Unit] = {
    client.registerEventHandler(1, handler, classOf[OnUpdateTipEventPayload])
  }

  override def unsubscribeOnUpdateTipEvent(handler: OnUpdateTipEventHandler): Unit = {
    client.unregisterEventHandler(1, handler)
  }
}
