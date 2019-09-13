package com.horizen.websocket

import java.util.concurrent.TimeUnit

import akka.pattern.ask
import akka.actor.{ActorRef, ActorSystem}
import akka.stream.ActorMaterializer
import akka.util.Timeout
import com.horizen.WebSocketClientSettings
import com.horizen.block.{MainchainBlockReference, MainchainBlockReferenceSerializer}
import com.horizen.websocket.WebSocketClient.ReceivableMessages.{SubscribeForEvent, UnSubscribeForEvent}
import io.circe.{Json, parser}
import scorex.util.ScorexLogging

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

class MainchainCommunicationChannel(webSocketConfiguration : WebSocketClientSettings)
                                   (implicit system : ActorSystem, materializer : ActorMaterializer, ec : ExecutionContext)
  extends ScorexLogging {

  private var webSocketClient: ActorRef = null

  implicit val timeout: Timeout = Timeout(
    webSocketConfiguration.responseTimeout,
    TimeUnit.valueOf(webSocketConfiguration.responseTimeUnit))

  def openCommunicationChannel() = {
    webSocketClient = WebSocketClientRef(webSocketConfiguration)
  }

  def getBlock(heightOrHash : Either[Int, String]): Try[MainchainBlockReference] = {

    var blockBytes: Array[Byte] = Array[Byte]()

    getSingleBlock(heightOrHash) match {
      case Success(value) =>
        value match {
          case Right(error) =>
            log.error("getSingleBlock: Response error --> " + error.toString)
            return Failure(new Exception(error.errorMessage))
          case Left(bl) =>
            blockBytes = bl.block.getBytes
        }
      case Failure(exception) =>
        log.error("getSingleBlock: Unexpected exception --> " + exception.getMessage)
        return Failure(exception)
    }

    MainchainBlockReferenceSerializer.parseBytesTry(blockBytes)

  }

  def getBlockHashes(lenght: Int, afterHeightOrAfterHash : Either[Int, String]): Try[Seq[String]] = {

    var hashes: Seq[String] = Seq[String]()

    getMultipleBlockHashes(lenght, afterHeightOrAfterHash) match {
      case Success(value) =>
        value match {
          case Right(error) =>
            log.error("getMultipleBlockHashes: Response error --> " + error.toString)
            return Failure(new Exception(error.errorMessage))
          case Left(bl) =>
            hashes = bl.hashes
        }
      case Failure(exception) =>
        log.error("getMultipleBlockHashes: Unexpected exception --> " + exception.getMessage)
        return Failure(exception)
    }

    Success(hashes)
  }

  def sync(hashes : Seq[String], lenght: Int): Try[Seq[String]] = {

    var respHashes: Seq[String] = Seq[String]()

    getSyncInfo(hashes, lenght) match {
      case Success(value) =>
        value match {
          case Right(error) =>
            log.error("getSyncInfo: Response error --> " + error.toString)
            return Failure(new Exception(error.errorMessage))
          case Left(bl) =>
            respHashes = bl.hashes
        }
      case Failure(exception) =>
        log.error("getSyncInfo: Unexpected exception --> " + exception.getMessage)
        return Failure(exception)
    }

    Success(respHashes)
  }

  private def getSingleBlock(heightOrHash : Either[Int, String]): Try[Either[GetSingleBlockResponse, ErrorResponse]] = {
    var response: Try[Either[GetSingleBlockResponse, ErrorResponse]] = Failure(new IllegalStateException("Parsing not yet completed"))
    val correlationId = generateCorrelationId()

    val fut = Await.result(
      (webSocketClient ? GetSingleBlock(correlationId, heightOrHash)),
      timeout.duration)
      .asInstanceOf[Future[ChannelMessage]]

    try {
      val webSocketResponse = Await.result(fut, timeout.duration)
      parseResponse(webSocketResponse).asInstanceOf[Try[Either[GetSingleBlockResponse, ErrorResponse]]]
    }catch {
      case e : Throwable =>
        Failure(e)
    }

  }

  private def getMultipleBlockHashes(lenght: Int, afterHeightOrAfterHash : Either[Int, String]): Try[Either[GetMultipleBlockHashesResponse, ErrorResponse]] = {
    var response: Try[Either[GetMultipleBlockHashesResponse, ErrorResponse]] = Failure(new IllegalStateException("Parsing not yet completed"))
    val correlationId = generateCorrelationId()

    val fut = Await.result(
      (webSocketClient ? GetMultipleBlockHashes(
        correlationId, lenght, afterHeightOrAfterHash)),
      timeout.duration)
      .asInstanceOf[Future[ChannelMessage]]

    try {
      val webSocketResponse = Await.result(fut, timeout.duration)
      parseResponse(webSocketResponse).asInstanceOf[Try[Either[GetMultipleBlockHashesResponse, ErrorResponse]]]
    }catch {
      case e : Throwable =>
        Failure(e)
    }

  }

  private def getSyncInfo(hashes : Seq[String], lenght: Int): Try[Either[GetMultipleBlockHashesResponse, ErrorResponse]] = {
    var response: Try[Either[GetMultipleBlockHashesResponse, ErrorResponse]] = Failure(new IllegalStateException("Parsing not yet completed"))
    val correlationId = generateCorrelationId()

    val fut = Await.result(
      (webSocketClient ? GetSyncInfo(correlationId, hashes, lenght)),
      timeout.duration)
      .asInstanceOf[Future[ChannelMessage]]

    try {
      val webSocketResponse = Await.result(fut, timeout.duration)
      parseResponse(webSocketResponse).asInstanceOf[Try[Either[GetMultipleBlockHashesResponse, ErrorResponse]]]
    }catch {
      case e : Throwable =>
        Failure(e)
    }

  }

  def subscribeOnEvent[E <: WebSocketEvent](f: E => Unit, clazz: Class[E]): String = {
    val fut =
      (
        webSocketClient ? SubscribeForEvent({
          event => {
            parseWebSocketEvent(event) match {
              case Success(value) =>
                value match {
                  case e: E =>
                    f(e)
                  case _ =>
                }
              case Failure(exception) => log.error(exception.getMessage)
            }
          }
        })
        )

    var result = Await.result(fut, timeout.duration).asInstanceOf[Future[String]]

    if (result.isCompleted)
      result.value.get.get
    else ""

  }

  def unSubscribeOnEvent(actorName: String) = {
    webSocketClient ! UnSubscribeForEvent(actorName)
  }

  private def parseWebSocketEvent(channelMessageEvent: ChannelMessageEvent): Try[WebSocketEvent] = {
    parser.decode[Map[String, Json]](channelMessageEvent.message) match {
      case Right(map) =>
        map.get("type") match {
          case Some(atype) =>
            try {
              atype.asNumber.get.toInt.get match {
                case 1 =>
                  var height = map.get("height").get.asNumber.get.toInt.getOrElse(-1)
                  var hash = map.get("hash").getOrElse("")
                  var block = map.get("block").getOrElse("")
                  Success(UpdateTipEvent(height, hash.toString, block.toString))
                case _ => Failure(new IllegalStateException("No event can be parsed"))
              }
            } catch {
              case e: Throwable => Failure(e)
            }
          case None => Failure(new IllegalStateException("No event can be parsed"))
        }
      case Left(value) => Failure(value)
    }
  }

  private def parseResponse(channelMessage : ChannelMessage) : Try[Either[WebSocketResponseMessage, ErrorResponse]] = {
    parser.decode[Map[String, Json]](channelMessage.message) match {
      case Right(map) =>

        var errorCode =
          try {
            map.get("errorCode").get.asNumber.get.toInt.getOrElse(Int.MaxValue)
          }catch {
            case _ => Int.MaxValue
          }
        var errorMessage = map.get("message").getOrElse("").toString

        errorCode match {
          case -1 => Success(Right(ErrorResponse(errorCode, errorMessage)))
          case _ =>
          {
            var reqType =
              try{
                map.get("type").get.asNumber.get.toInt.getOrElse(-1)
              }catch {
                case _ => -1
              }
            var height =
              try {
                map.get("height").get.asNumber.get.toInt.getOrElse(-1)
              }catch {
                case _ => -1
              }
            var hash = map.get("hash").getOrElse("").toString
            var block = map.get("block").getOrElse("").toString
            var corrId = map.get("msgId").getOrElse("").toString

            reqType match {
              case 2 if errorCode<Int.MaxValue => Success(Right(ErrorResponse(errorCode, errorMessage)))
              case 3 if errorCode<Int.MaxValue => Success(Right(ErrorResponse(errorCode, errorMessage)))
              case 2 => Success(Left(GetSingleBlockResponse(corrId, height, hash, block)))
              case 3 | 4 =>
              {
                var hashes : Seq[String] = Seq()
                map.get("hashes") match {
                  case Some(value) =>
                    if(value.isArray)
                    {
                      var jsonHashes = value.asArray.get
                      jsonHashes.foreach(js => {
                        if(js.isString){
                          hashes = hashes :+ (js.asString.get)
                        }
                      })
                    }
                  case None =>
                }
                Success(Left(GetMultipleBlockHashesResponse(corrId, height, hashes)))
              }
              case _ => Failure(new IllegalStateException("No response can be parsed"))
            }
          }
        }
      case Left(value) => Failure(value)
    }

  }



  private def generateCorrelationId() : String = {
    String.valueOf(System.currentTimeMillis())
  }
}