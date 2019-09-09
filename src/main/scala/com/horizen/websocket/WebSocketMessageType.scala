package com.horizen.websocket

import io.circe.Json

trait WebSocketResponseMessage {

}

case class ErrorResponse(
                          var errorCode : Int,
                          var errorMessage : String
                        ) extends WebSocketResponseMessage {

}

case class GetSingleBlockResponse(
                                   var correlationId : String,
                                   var height : Int,
                                   var hash : String,
                                   var block : String) extends WebSocketResponseMessage

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

    jsonTuple = jsonTuple ++ updateJsonWithMembers()

    Json.fromFields(jsonTuple)
  }
}

abstract class BlockRequest extends WebSocketRequestMessage {

}

case class GetSingleBlock (
                            override var correlationId : String,
                            var heightOrHash : Either[Int, String]) extends BlockRequest {
  override var command = "getBlock"

  override def updateJsonWithMembers(): Seq[(String, Json)] = {
    var jsonTuple : Seq[(String, Json)] = Seq[(String, Json)]()

    heightOrHash match {
      case Left(value) => jsonTuple = jsonTuple :+ ("height", Json.fromInt(value))
      case Right(value) => jsonTuple = jsonTuple :+ ("hash", Json.fromString(value))
    }

    jsonTuple
  }
}

case class GetMultipleBlockHashes (
                                    override var correlationId : String,
                                    var lenght : Int,
                                    var afterHeightOrAfterHash : Either[Int, String]) extends BlockRequest {
  override var command = "getBlockHashes"

  override def updateJsonWithMembers(): Seq[(String, Json)] = {
    var jsonTuple : Seq[(String, Json)] = Seq[(String, Json)](("len", Json.fromInt(lenght)))

    afterHeightOrAfterHash match {
      case Left(value) => jsonTuple = jsonTuple :+ ("afterHeight", Json.fromInt(value))
      case Right(value) => jsonTuple = jsonTuple :+ ("afterHash", Json.fromString(value))
    }

    jsonTuple
  }
}

case class GetSyncInfo(
                        override var correlationId : String,
                        var hashes : Seq[String],
                        var lenght : Int
                      ) extends WebSocketRequestMessage {
  override var command: String = "getSyncInfo"

  override def updateJsonWithMembers(): Seq[(String, Json)] = Seq[(String, Json)](
    ("hashes", Json.fromValues(hashes.map(hash => Json.fromString(hash)))),
    ("len", Json.fromInt(lenght))
  )
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
