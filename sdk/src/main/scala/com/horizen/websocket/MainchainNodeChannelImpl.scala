package com.horizen.websocket
import com.horizen.block.MainchainBlockReference
import com.horizen.params.NetworkParams
import com.horizen.utils.BytesUtils

import scala.util.{Failure, Success, Try}
import scala.concurrent.{Await, Future}


case class GetBlockByHeightRequestPayload(height: Int) extends RequestPayload
case class GetBlockByHashRequestPayload(hash: String) extends RequestPayload
case class GetBlocksAfterHeightRequestPayload(afterHeight: Int, limit: Int) extends RequestPayload
case class GetBlocksAfterHashRequestPayload(afterHash: String, limit: Int) extends RequestPayload
case class GetNewBlocksRequestPayload(locatorHashes: Seq[String], limit: Int) extends RequestPayload


case class BlockResponsePayload(height: Int, hash: String, block: String) extends ResponsePayload
case class BlocksResponsePayload(height: Int, hashes: Seq[String]) extends ResponsePayload
case class NewBlocksResponsePayload(height: Int, hashes: Seq[String]) extends ResponsePayload


class MainchainNodeChannelImpl(client: CommunicationClient, params: NetworkParams) extends MainchainNodeChannel { // to do: define EC inside?

  override def getBlockByHeight(height: Int): Try[MainchainBlockReference] = Try {
    val future: Future[BlockResponsePayload] =
      client.sendRequest(0, GetBlockByHeightRequestPayload(height), classOf[BlockResponsePayload])

    processBlockResponsePayload(future).get
  }

  override def getBlockByHash(hash: String): Try[MainchainBlockReference] = Try {
    val future: Future[BlockResponsePayload] =
      client.sendRequest(0, GetBlockByHashRequestPayload(hash), classOf[BlockResponsePayload])

    processBlockResponsePayload(future).get
  }

  private def processBlockResponsePayload(future: Future[BlockResponsePayload]): Try[MainchainBlockReference] = Try {
    val response: BlockResponsePayload = Await.result(future, client.requestTimeoutDuration())
    val blockBytes = BytesUtils.fromHexString(response.block)
    MainchainBlockReference.create(blockBytes, params).get
  }

  def getBlockHashesAfterHeight(height: Int, limit: Int): Try[Seq[String]] = Try {
    val future: Future[BlocksResponsePayload] =
      client.sendRequest(1, GetBlocksAfterHeightRequestPayload(height, limit), classOf[BlocksResponsePayload])

    val response: BlocksResponsePayload = Await.result(future, client.requestTimeoutDuration())
    response.hashes
  }

  def getBlockHashesAfterHash(hash: String, limit: Int): Try[Seq[String]] = Try {
    val future: Future[BlocksResponsePayload] =
      client.sendRequest(1, GetBlocksAfterHashRequestPayload(hash, limit), classOf[BlocksResponsePayload])

    val response: BlocksResponsePayload = Await.result(future, client.requestTimeoutDuration())
    response.hashes
  }


  override def getNewBlockHashes(locatorHashes: Seq[String], limit: Int): Try[(Int, Seq[String])] = Try {
    val future: Future[NewBlocksResponsePayload] =
      client.sendRequest(2, GetNewBlocksRequestPayload(locatorHashes, limit), classOf[NewBlocksResponsePayload])

    val response: NewBlocksResponsePayload = Await.result(future, client.requestTimeoutDuration())
    (response.height, response.hashes)
  }

  override def getBestCommonPoint(locatorHashes: Seq[String]): Try[(Int, String)] = {
    getNewBlockHashes(locatorHashes, 1) match {
      case Success((height, hashes)) => Success(height, hashes.head)
      case Failure(ex) => throw ex
    }
  }

  override def subscribeOnUpdateTipEvent(handler: OnUpdateTipEventHandler): Try[Unit] = {
    client.registerEventHandler(0, handler, classOf[OnUpdateTipEventPayload])
  }

  override def unsubscribeOnUpdateTipEvent(handler: OnUpdateTipEventHandler): Unit = {
    client.unregisterEventHandler(0, handler)
  }
}
