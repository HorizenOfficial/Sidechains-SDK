package com.horizen.websocket

import io.circe.Json

trait WebSocketResponseMessage {

}

case class ErrorResponse(
                          var errorCode : Int,
                          var errorMessage : String
                        ) extends WebSocketResponseMessage {

}

abstract class BlockResponse extends WebSocketResponseMessage {
  protected var correlationId : String
  protected var height : Int
  protected var hash : String
  protected var block : String
}

case class GetSingleBlockResponse(
                                   var correlationId : String,
                                   var height : Int,
                                   var hash : String,
                                   var block : String) extends BlockResponse

case class GetMultipleBlocksResponse(
                                      var correlationId : String,
                                      var counter : Int,
                                      var height : Int,
                                      var hash : String,
                                      var block : String) extends BlockResponse

case class GetMultipleBlockHashesResponse(
                                   var correlationId : String,
                                   height : Int,
                                   hashes : Seq[String]) extends WebSocketResponseMessage


trait WebSocketRequestMessage {

  var correlationId : String
  var command : String

  def updateJsonWithMembers(): Seq[(String, Json)]

  def toJson : Json = {
    var jsonTuple : Seq[(String, Json)] = Seq[(String, Json)]()

    jsonTuple = jsonTuple :+ ("msgId", Json.fromString(correlationId))
    jsonTuple = jsonTuple :+ ("command", Json.fromString(command))

    jsonTuple :+ updateJsonWithMembers()

    var jsonObj = Json.fromFields(jsonTuple)

    jsonObj
  }
}

abstract class BlockRequest extends WebSocketRequestMessage {

  protected var lenght : Int
  protected var afterHeight : Option[Int]
  protected var afterHash : Option[String]

  def updateBlockRequest(): Seq[(String, Json)]

  override def updateJsonWithMembers(): Seq[(String, Json)] = {
    var jsonTuple = Seq[(String, Json)]()

    jsonTuple = jsonTuple :+  ("lenght", Json.fromInt(lenght))

    if(!afterHash.isEmpty)
      jsonTuple = jsonTuple :+ ("afterHash", Json.fromString(afterHash.get))
    if(!afterHeight.isEmpty)
      jsonTuple = jsonTuple :+ ("afterHeight", Json.fromInt(afterHeight.get))

    jsonTuple :+ updateBlockRequest()

    jsonTuple
  }
}

case class GetSingleBlock (
                            override var correlationId : String,
                            var afterHeight : Option[Int],
                            var afterHash : Option[String]) extends BlockRequest {
  override var command = "getBlock"
  override var lenght : Int = 1

  override def updateBlockRequest(): Seq[(String, Json)] = Seq()
}

case class GetMultipleBlockHashes (
                                    override var correlationId : String,
                                    var lenght : Int,
                                    var afterHeight : Option[Int],
                                    var afterHash : Option[String]) extends BlockRequest {
  override var command = "getBlockHashes"

  override def updateBlockRequest(): Seq[(String, Json)] = Seq()
}

case class ChannelMessageEvent(message : String){}
case class ChannelMessage(message : String){}

trait WebSocketEvent {
}

case class UpdateTipEvent(
                           height : Int,
                           hash : String,
                           block : String) extends WebSocketEvent {
}
