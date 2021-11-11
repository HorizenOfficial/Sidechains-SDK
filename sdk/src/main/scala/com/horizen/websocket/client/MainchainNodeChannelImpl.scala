package com.horizen.websocket.client
import com.horizen.block.{MainchainBlockReference, MainchainHeader}
import com.horizen.mainchain.api.{SendCertificateRequest, SendCertificateResponse}
import com.horizen.params.NetworkParams
import com.horizen.utils.BytesUtils

import scala.concurrent.{Await, Future}
import scala.util.{Failure, Success, Try}


case class GetBlockByHeightRequestPayload(height: Int) extends RequestPayload
case class GetBlockByHashRequestPayload(hash: String) extends RequestPayload
case class GetBlocksAfterHeightRequestPayload(afterHeight: Int, limit: Int) extends RequestPayload
case class GetBlocksAfterHashRequestPayload(afterHash: String, limit: Int) extends RequestPayload
case class GetNewBlocksRequestPayload(locatorHashes: Seq[String], limit: Int) extends RequestPayload
case class GetBlockHeadersRequestPayload(hashes: Seq[String]) extends RequestPayload
case class BackwardTransfer(address: String, amount: String)
case class SendCertificateRequestPayload(scid: String,
                                         epochNumber: Int,
                                         quality: Long,
                                         endEpochCumCommTreeHash: String,
                                         scProof: String,
                                         backwardTransfers: Seq[BackwardTransfer],
                                         forwardTransferScFee: String, // Decimal string
                                         mainchainBackwardTransferScFee: String, // Decimal string
                                         fee: String, // Decimal string
                                         vFieldElementCertificateField: Seq[String], // Seq of compressed FE hex strings
                                         vBitVectorCertificateField: Seq[String] // Seq of compressed bitvectors hex strings
                                        ) extends RequestPayload
case class TopQualityCertificatePayload(scid: String) extends RequestPayload



case class BlockResponsePayload(height: Int, hash: String, block: String) extends ResponsePayload
case class BlocksResponsePayload(height: Int, hashes: Seq[String]) extends ResponsePayload
case class NewBlocksResponsePayload(height: Int, hashes: Seq[String]) extends ResponsePayload
case class BlockHeadersResponsePayload(headers: Seq[String]) extends ResponsePayload
case class CertificateResponsePayload(certificateHash: String) extends ResponsePayload

case class MempoolTopQualityCertificateInfo(certHash: String, epoch: Int, quality: Long, fee: Double)
case class ChainTopQualityCertificateInfo(certHash: String, epoch: Int, quality: Long)

case class TopQualityCertificateResponsePayload(mempoolTopQualityCert: Option[MempoolTopQualityCertificateInfo],
                                                chainTopQualityCert: Option[ChainTopQualityCertificateInfo]) extends ResponsePayload


case object GET_SINGLE_BLOCK_REQUEST_TYPE extends RequestType(0)
case object GET_MULTIPLE_BLOCK_HASHES_REQUEST_TYPE extends RequestType(1)
case object GET_NEW_BLOCK_HASHES_REQUEST_TYPE extends RequestType(2)
case object SEND_CERTIFICATE_REQUEST_TYPE extends RequestType(3)
case object GET_MULTIPLE_HEADERS_REQUEST_TYPE extends RequestType(4)
case object GET_TOP_QUALITY_CERTIFICATES_TYPE extends RequestType(5)

class MainchainNodeChannelImpl(client: CommunicationClient, params: NetworkParams) extends MainchainNodeChannel { // to do: define EC inside?

  override def getBlockByHeight(height: Int): Try[MainchainBlockReference] = Try {
    val future: Future[BlockResponsePayload] =
      client.sendRequest(GET_SINGLE_BLOCK_REQUEST_TYPE, GetBlockByHeightRequestPayload(height), classOf[BlockResponsePayload])

    processBlockResponsePayload(future).get
  }

  override def getBlockByHash(hash: String): Try[MainchainBlockReference] = Try {
    val future: Future[BlockResponsePayload] =
      client.sendRequest(GET_SINGLE_BLOCK_REQUEST_TYPE, GetBlockByHashRequestPayload(hash), classOf[BlockResponsePayload])

    processBlockResponsePayload(future).get
  }

  private def processBlockResponsePayload(future: Future[BlockResponsePayload]): Try[MainchainBlockReference] = Try {
    val response: BlockResponsePayload = Await.result(future, client.requestTimeoutDuration())
    val blockBytes = BytesUtils.fromHexString(response.block)
    MainchainBlockReference.create(blockBytes, params).get
  }

  def getBlockHashesAfterHeight(height: Int, limit: Int): Try[Seq[String]] = Try {
    val future: Future[BlocksResponsePayload] =
      client.sendRequest(GET_MULTIPLE_BLOCK_HASHES_REQUEST_TYPE, GetBlocksAfterHeightRequestPayload(height, limit), classOf[BlocksResponsePayload])

    val response: BlocksResponsePayload = Await.result(future, client.requestTimeoutDuration())
    response.hashes
  }

  def getBlockHashesAfterHash(hash: String, limit: Int): Try[Seq[String]] = Try {
    val future: Future[BlocksResponsePayload] =
      client.sendRequest(GET_MULTIPLE_BLOCK_HASHES_REQUEST_TYPE, GetBlocksAfterHashRequestPayload(hash, limit), classOf[BlocksResponsePayload])

    val response: BlocksResponsePayload = Await.result(future, client.requestTimeoutDuration())
    response.hashes
  }


  override def getNewBlockHashes(locatorHashes: Seq[String], limit: Int): Try[(Int, Seq[String])] = Try {
    val future: Future[NewBlocksResponsePayload] =
      client.sendRequest(GET_NEW_BLOCK_HASHES_REQUEST_TYPE, GetNewBlocksRequestPayload(locatorHashes, limit), classOf[NewBlocksResponsePayload])

    val response: NewBlocksResponsePayload = Await.result(future, client.requestTimeoutDuration())
    (response.height, response.hashes)
  }

  override def getBestCommonPoint(locatorHashes: Seq[String]): Try[(Int, String)] = {
    getNewBlockHashes(locatorHashes, 1) match {
      case Success((height, hashes)) => Success(height, hashes.head)
      case Failure(ex) => throw ex
    }
  }

  override def getBlockHeaders(hashes: Seq[String]): Try[Seq[MainchainHeader]] = Try {
    val future: Future[BlockHeadersResponsePayload] =
      client.sendRequest(GET_MULTIPLE_HEADERS_REQUEST_TYPE, GetBlockHeadersRequestPayload(hashes), classOf[BlockHeadersResponsePayload])

    processBlockHeadersResponsePayload(future).get
  }

  private def processBlockHeadersResponsePayload(future: Future[BlockHeadersResponsePayload]): Try[Seq[MainchainHeader]] = Try {
    val response: BlockHeadersResponsePayload = Await.result(future, client.requestTimeoutDuration())

    val strHeaders: Seq[String] = response.headers
    val headers: Seq[MainchainHeader] = strHeaders.map(str => MainchainHeader.create(BytesUtils.fromHexString(str), 0).get)

    headers
  }

  override def subscribeOnUpdateTipEvent(handler: OnUpdateTipEventHandler): Try[Unit] = {
    client.registerEventHandler(0, handler, classOf[OnUpdateTipEventPayload])
  }

  override def unsubscribeOnUpdateTipEvent(handler: OnUpdateTipEventHandler): Unit = {
    client.unregisterEventHandler(0, handler)
  }

  override def sendCertificate(certificateRequest: SendCertificateRequest): Try[SendCertificateResponse] = Try {
    val backwardTransfers: Seq[BackwardTransfer] = certificateRequest.backwardTransfers.map(bt =>
      BackwardTransfer(bt.address, bt.amount))

    val fee: String = certificateRequest.fee match {
      case Some(fee) => fee
      case None => "-1"
    }

    val requestPayload:SendCertificateRequestPayload = SendCertificateRequestPayload(
        BytesUtils.toHexString(certificateRequest.sidechainId),
        certificateRequest.epochNumber,
        certificateRequest.quality,
        BytesUtils.toHexString(certificateRequest.endEpochCumCommTreeHash),
        BytesUtils.toHexString(certificateRequest.proofBytes),
        backwardTransfers,
        certificateRequest.ftrMinAmount,
        certificateRequest.btrMinFee,
        fee,
        certificateRequest.fieldElementCertificateFields.map(BytesUtils.toHexString),
        certificateRequest.bitVectorCertificateFields.map(BytesUtils.toHexString))

    val future: Future[CertificateResponsePayload] = client.sendRequest(SEND_CERTIFICATE_REQUEST_TYPE, requestPayload, classOf[CertificateResponsePayload])

    processCertificateResponsePayload(future)
  }

  private def processCertificateResponsePayload(future: Future[CertificateResponsePayload]): SendCertificateResponse = {
    val response: CertificateResponsePayload = Await.result(future, client.requestTimeoutDuration())
    SendCertificateResponse(BytesUtils.fromHexString(response.certificateHash))
  }

  override def getTopQualityCertificates(scId: String): Try[TopQualityCertificates] = Try {
    val future: Future[TopQualityCertificateResponsePayload] =
      client.sendRequest(GET_TOP_QUALITY_CERTIFICATES_TYPE, TopQualityCertificatePayload(scId), classOf[TopQualityCertificateResponsePayload])

    val response: TopQualityCertificateResponsePayload = Await.result(future, client.requestTimeoutDuration())
    TopQualityCertificates(response.mempoolTopQualityCert, response.chainTopQualityCert)
  }
}
