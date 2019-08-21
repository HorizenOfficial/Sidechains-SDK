package com.horizen.websocket

import io.circe.{Json, parser}

/**
  * The meaning of this class is the following:
  * - from WebSocketClient perspective: I don't know, and I don't need to know, how to parse a web socket response.
  *     I only need a correlation id and the type of the request in order to correctly process my request
  * - from MainchainCommunicationChannel: I know someone parsed the json response from mainchain. It already discovered
  *     the correlation id and the request type. So, since I know how to parse the response, I can continue to use this class
  */
class WebSocketResponseParsingState {

  private var jsonMap : Map[String, Json] = null
  private var correlationId : String = ""
  private var requestType : Int = Int.MaxValue

  def getJsonMap() :Map[String, Json] = jsonMap

  def getCorrelationId() : String = correlationId

  def getRequestType() : Int = requestType

  private def isWellFormedJson(rawResponse : String) : Boolean = {
    try {
      parser.decode[Map[String, Json]](rawResponse) match {
        case Right(map) =>
          jsonMap = map
          true
        case Left(value) => false
      }
    }catch {
      case e : Throwable => false
    }
  }

  private def findCorrelationId() : String = {
    var correlationId : String = null
    try {
      jsonMap.get("msgId") match {
        case Some(value) =>
          if(value.isString)
            correlationId = value.asString.get
        case None =>
      }
    }catch {
      case e : Throwable =>
    }

    return correlationId
  }

  private def findRequestType() : Int = {
    var requestType = Int.MaxValue
    try {
      jsonMap.get("type") match {
        case Some(value) =>
          if (value.isNumber)
            requestType = value.asNumber.get.toInt.get
          case None =>
        }
    } catch {
        case e: Throwable =>
    }

    return requestType
  }

  def fromJsonString(rawResponse : String) = {
    if(isWellFormedJson(rawResponse)){
      requestType = findRequestType()
      correlationId = findCorrelationId()
    }
  }

}
