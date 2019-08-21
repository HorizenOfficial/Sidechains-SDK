package com.horizen.websocket

import java.util.concurrent.TimeUnit

import akka.pattern.ask
import akka.actor.{ActorRef, ActorSystem}
import akka.stream.ActorMaterializer
import akka.util.Timeout
import com.horizen.WebSocketClientSettings
import com.horizen.block.{MainchainBlockReference, MainchainBlockReferenceSerializer}
import com.horizen.websocket.WebSocketClient.ReceivableMessages.{StartResponseFlow, SubscribeForEvent, UnSubscribeForEvent}
import io.circe.{Json, parser}

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

class MainchainCommunicationChannel(webSocketConfiguration : WebSocketClientSettings)
                                   (implicit system : ActorSystem, materializer : ActorMaterializer, ec : ExecutionContext){

  private var webSocketClient : ActorRef = null
  implicit var timeout : Timeout = null

  def openCommunicationChannel() = {
    timeout = Timeout(
      webSocketConfiguration.responseTimeout,
      TimeUnit.valueOf(webSocketConfiguration.responseTimeUnit))

    webSocketClient = WebSocketClientRef(webSocketConfiguration)
  }

  def getBlock(afterHeight : Option[Int], afterHash : Option[String]) : Try[MainchainBlockReference] = {

    val blockResponse = getSingleBlock(afterHeight, afterHash)

    var blockBytes : Array[Byte] = Array[Byte]()

    blockResponse match {
      case Success(value) =>
        value match {
          case Right(error) => println("getSingleBlock: Response error --> " + error.toString)
          case Left(bl) =>
            println ("getSingleBlock -->  " + bl.toString)
            blockBytes = bl.block.getBytes
        }
      case Failure(exception) => println("getSingleBlock: Unexpected exception --> " + exception.getMessage)
    }

    var mainchainBlock = MainchainBlockReferenceSerializer.parseBytesTry(blockBytes)


    return mainchainBlock

  }

  def getBlocks(lenght : Int, afterHeight : Option[Int], afterHash : Option[String]) : Seq[Try[MainchainBlockReference]] = {

    val seqOfBlockResponses = getMultipleBlocks(lenght, afterHeight, afterHash)

    var seqOfBlockBytes : Seq[Array[Byte]] = Seq[Array[Byte]]()

    seqOfBlockResponses.foreach(
      blockResponse =>
        {
          blockResponse match {
            case Success(value) =>
              value match {
                case Right(error) => println("getMultipleBlocks: Response error --> " + error.toString)
                case Left(bl) =>
                  println ("getMultipleBlocks -->  " + bl.toString)
                  seqOfBlockBytes = seqOfBlockBytes :+ (bl.block.getBytes)
              }
            case Failure(exception) => println("getMultipleBlocks: Unexpected exception --> " + exception.getMessage)
          }
        }
    )

    var seqOfMainchainBlocks : Seq[Try[MainchainBlockReference]] = Seq[Try[MainchainBlockReference]]()

    seqOfBlockBytes.foreach(
      blockBytes =>
          {
            var mainchainBlock = MainchainBlockReferenceSerializer.parseBytesTry(blockBytes)
            seqOfMainchainBlocks = seqOfMainchainBlocks :+ mainchainBlock
          }
    )

    return seqOfMainchainBlocks

  }

  def getBlockHashes(lenght : Int, afterHeight : Option[Int], afterHash : Option[String]) : Seq[String] = {

    val blockHashesResponse = getMultipleBlockHashes(lenght, afterHeight, afterHash)

    var hashes : Seq[String] = Seq[String]()

    blockHashesResponse match {
      case Success(value) =>
        value match {
          case Right(error) => println("getMultipleBlockHashes: Response error --> " + error.toString)
          case Left(bl) =>
            println ("getMultipleBlockHashes -->  " + bl.toString)
            hashes = bl.hashes
        }
      case Failure(exception) => println("getMultipleBlockHashes: Unexpected exception --> " + exception.getMessage)
    }

    return hashes
  }

  private def getSingleBlock(afterHeight : Option[Int], afterHash : Option[String]) : Try[Either[GetSingleBlockResponse, ErrorResponse]] = {
    var response : Try[Either[GetSingleBlockResponse, ErrorResponse]] = Failure(new IllegalStateException("Parsing not yet completed"))
    val correlationId = generateCorrelationId()

    val fut = Await.result(
      (webSocketClient ? GetSingleBlock(correlationId, afterHeight, afterHash)),
      timeout.duration)
      .asInstanceOf[Future[WebSocketResponseParsingState]]

    val webSocketResponse = Await.result(fut, timeout.duration)

    response = parseResponse(webSocketResponse).asInstanceOf[Try[Either[GetSingleBlockResponse, ErrorResponse]]]

    return response
  }

  private def getMultipleBlocks(lenght : Int, afterHeight : Option[Int], afterHash : Option[String]) : Seq[Try[Either[GetMultipleBlocksResponse, ErrorResponse]]] = {
    var response : Try[Either[GetMultipleBlocksResponse, ErrorResponse]] = Failure(new IllegalStateException("Parsing not yet completed"))
    val correlationId = generateCorrelationId()

    var seqOfResponses : Seq[Try[Either[GetMultipleBlocksResponse, ErrorResponse]]] = Seq()

    val flowFut = Await.result(
      (webSocketClient ? StartResponseFlow(correlationId, lenght-1)),
      timeout.duration)
      .asInstanceOf[Future[Seq[WebSocketResponseParsingState]]]

    val fut = Await.result(
      (webSocketClient ? GetMultipleBlocks(
        correlationId, lenght, afterHeight, afterHash)),
      timeout.duration)
      .asInstanceOf[Future[WebSocketResponseParsingState]]

    val webSocketResponse = Await.result(fut, timeout.duration)

    val flowTimeout = Timeout(webSocketConfiguration.responseTimeout * 2, TimeUnit.valueOf(webSocketConfiguration.responseTimeUnit))
    val seqOfWebSocketResponse = Await.result(flowFut, flowTimeout.duration)

    response = parseResponse(webSocketResponse).asInstanceOf[Try[Either[GetMultipleBlocksResponse, ErrorResponse]]]

    seqOfResponses = seqOfResponses :+ response

    seqOfWebSocketResponse.foreach(
      resp =>
        {
          var parsedResp = parseResponse(resp).asInstanceOf[Try[Either[GetMultipleBlocksResponse, ErrorResponse]]]
          seqOfResponses = seqOfResponses :+ parsedResp
        })

    return seqOfResponses
  }

  private def getMultipleBlockHashes(lenght : Int, afterHeight : Option[Int], afterHash : Option[String]) : Try[Either[GetMultipleBlockHashesResponse, ErrorResponse]] = {
    var response : Try[Either[GetMultipleBlockHashesResponse, ErrorResponse]] = Failure(new IllegalStateException("Parsing not yet completed"))
    val correlationId = generateCorrelationId()

    val fut = Await.result(
      (webSocketClient ? GetMultipleBlockHashes(
        correlationId, lenght, afterHeight, afterHash)),
      timeout.duration)
      .asInstanceOf[Future[WebSocketResponseParsingState]]

    val webSocketResponse = Await.result(fut, timeout.duration)

    response = parseResponse(webSocketResponse).asInstanceOf[Try[Either[GetMultipleBlockHashesResponse, ErrorResponse]]]

    return response
  }

  def onUpdateTipEvent(f : UpdateTipEvent => Unit) : String = {
    val fut =
      (
        webSocketClient ? SubscribeForEvent[UpdateTipEvent]({
          event => {
            var updateTipEvent = buildWebSocketEvent(event)
            if(updateTipEvent.isInstanceOf[UpdateTipEvent])
              f(updateTipEvent.asInstanceOf[UpdateTipEvent])
          }
        }, classOf[UpdateTipEvent])
      )

    var result = Await.result(fut, timeout.duration).asInstanceOf[Future[String]]

    var actorName = ""

    if(result.isCompleted)
      actorName = result.value.get.get

    return actorName
  }

  def stopOnUpdateTipEvent(actorName : String) = {
    webSocketClient ! UnSubscribeForEvent(actorName)
  }

  private def buildWebSocketEvent(notCompletedEvent : WebSocketEvent) : WebSocketEvent = {
    var eType = notCompletedEvent.eventType
    if(eType == 1){
      var msg = notCompletedEvent.message
      try {
        parser.decode[Map[String, Json]](msg) match {
          case Right(map) =>{
            var height : Int = -1
            var hash : String = ""
            var block : String = ""

            map.get("height") match {
              case Some(value) =>
                if(value.isNumber)
                  height = value.asNumber.get.toInt.get
              case None =>
            }

            map.get("hash") match {
              case Some(value) =>
                if(value.isString)
                  hash = value.asString.get
              case None =>
            }

            map.get("block") match {
              case Some(value) =>
                if(value.isString)
                  block = value.asString.get
              case None =>
            }

            return UpdateTipEvent("", height, hash, block)
          }
          case Left(value) => return notCompletedEvent
        }
      }catch {
        case e : Throwable => return notCompletedEvent
      }
    }else return notCompletedEvent
  }

  private def parseResponse(responseParsingState : WebSocketResponseParsingState) : Try[Either[WebSocketResponseMessage, ErrorResponse]] = {
    try {
      var map : Map[String, Json] = responseParsingState.getJsonMap()

      var reqType = responseParsingState.getRequestType()

      var response : Either[WebSocketResponseMessage, ErrorResponse] = null

      if(reqType == -1){
        var errorCode : Option[Int] = None
        var message : Option[String] = None

        map.get("errorCode") match {
          case Some(value) =>
            if(value.isNumber)
              errorCode = Some(value.asNumber.get.toInt.get)
          case None =>
        }

        map.get("message") match {
          case Some(value) =>
            if(value.isString)
              message = Some(value.asString.get)
          case None =>
        }

        response = Right(ErrorResponse(reqType, errorCode.get, message.get))

      }else{
        var corrId = responseParsingState.getCorrelationId()

        var height : Option[Int] = None
        var hash : Option[String] = None
        var block : Option[String] = None
        var errorCode : Option[Int] = None
        var message : Option[String] = None

        map.get("height") match {
          case Some(value) =>
            if(value.isNumber)
              height = Some(value.asNumber.get.toInt.get)
          case None =>
        }

        map.get("hash") match {
          case Some(value) =>
            if(value.isString)
              hash = Some(value.asString.get)
          case None =>
        }

        map.get("block") match {
          case Some(value) =>
            if(value.isString)
              block = Some(value.asString.get)
          case None =>
        }

        map.get("errorCode") match {
          case Some(value) =>
            if(value.isNumber)
              errorCode = Some(value.asNumber.get.toInt.get)
          case None =>
        }

        map.get("message") match {
          case Some(value) =>
            if(value.isString)
              message = Some(value.asString.get)
          case None =>
        }

        if(reqType == 2){
          if(errorCode.isEmpty){
            response = Left(GetSingleBlockResponse(corrId, reqType, height.get, hash.get, block.get))
          }else
            response = Right(ErrorResponse(reqType, errorCode.get, message.get))
        }
        else if(reqType == 3){
          if(errorCode.isEmpty){

            var counter : Int = -1

            map.get("counter") match {
              case Some(value) =>
                if(value.isNumber)
                  counter = value.asNumber.get.toInt.get
              case None =>
            }

            response = Left(GetMultipleBlocksResponse(corrId, reqType, counter, height.get, hash.get, block.get))

          }else
            response = Right(ErrorResponse(reqType, errorCode.get, message.get))
        }
        else if(reqType == 4){
          if(errorCode.isEmpty){

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

            response = Left(GetMultipleBlockHashesResponse(corrId, reqType, height.get, hashes))
          }else
            response = Right(ErrorResponse(reqType, errorCode.get, message.get))
        }

      }

      return Success(response)

    }catch {
      case e : Throwable => Failure(e)
    }
  }



  private def generateCorrelationId() : String = {
    String.valueOf(System.currentTimeMillis())
    /**
      * Only for simple test case during development.
      * Please, don't remove until the code will be accepted into the remote repository
      */
    //"1234"
  }
}
