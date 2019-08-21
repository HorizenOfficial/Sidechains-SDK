package com.horizen.websocket

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.stream.ActorMaterializer
import com.horizen.WebSocketClientSettings
import com.horizen.websocket.WebSocketEventActor.ReceivableMessages.{Subscribe, UnSubscribe}
import com.horizen.websocket.WebSocketChannel.ReceivableMessages.{OpenChannel, ReceiveMessage, SendMessage}
import com.horizen.websocket.WebSocketClient.ReceivableMessages.{StartResponseFlow, SubscribeForEvent, UnSubscribeForEvent}
import scorex.util.ScorexLogging

import scala.collection.concurrent.TrieMap
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.{Failure, Success, Try}

class WebSocketClient(webSocketConfiguration : WebSocketClientSettings)
                     (implicit system : ActorSystem, materializer : ActorMaterializer, ec : ExecutionContext) extends Actor with ScorexLogging{

  private var webSocketChannel : ActorRef = null
  private var requestPool : TrieMap[String, Promise[WebSocketResponseParsingState]] = TrieMap()
  private var eventActorPool : TrieMap[String, ActorRef] = TrieMap()
  private var responseFlowPoolPromises : TrieMap[String, Promise[Seq[WebSocketResponseParsingState]]] = TrieMap()
  private var responseFlowPool : TrieMap[String, (Int, Seq[WebSocketResponseParsingState])] = TrieMap()
  private var lastPromise : Promise[WebSocketResponseParsingState] = null

  override def preStart(): Unit = {
    webSocketChannel = WebSocketChannelRef(self, webSocketConfiguration)

    webSocketChannel ! OpenChannel
  }

  protected def manageRequest : Receive = {

    case req : WebSocketRequestMessage =>
      val promise = Promise[WebSocketResponseParsingState]
      requestPool += (req.correlationId -> promise)
      lastPromise = promise
      sender() ! promise.future
      log.info(s"Sending request to web socket channel: "+req.toString)
      webSocketChannel ! SendMessage(req.toJson.toString())

    case ReceiveMessage(message, throwable) =>
      if(throwable == null)
        log.info(s"Response from web socket channel: "+message)
      else
        log.error("Error from web socket channel", throwable)

      var response : Try[Either[WebSocketResponseParsingState, _ <: WebSocketEvent]] = getResponseFromRawResponse(message, throwable)
      response match {
        case Success(value) =>
          value match {
            case Right(event) =>
              context.system.eventStream.publish(event)
            case Left(resp) =>
              var correlationId = resp.getCorrelationId()
              requestPool.get(correlationId) match {
                case Some(promise) =>
                  if(throwable != null)
                    promise.failure(throwable)
                  else
                    promise.success(resp)
                  requestPool -= correlationId
                case None =>
                  // Maybe there is a flow of responses, due to StartResponseFlow message
                  responseFlowPool.get(correlationId) match {
                    case Some(pair) =>
                      var count = pair._1
                      var set = pair._2
                      set = set :+ resp
                      responseFlowPool.replace(correlationId, (count, set))
                      /**
                        * The size of 'set' variable is not the total expected responses, but the latter minus 1.
                        * Because the first response is provided in the 'Some (promise)' case
                        */
                      if(set.size == count){
                        responseFlowPoolPromises.get(correlationId) match {
                          case Some(promise) =>
                            promise.success(set)
                            responseFlowPoolPromises -= correlationId
                            responseFlowPool -= correlationId
                          case None =>
                        }

                      }
                    case None =>
                      /**
                        * Maybe there is a response error. I don't know about correlation id,
                        * and I don't know if someone is calling me synchronously. In any case,
                        * since I cannot know how to parse the response, I send it back to the last client who called me.
                        * It can handle the response better than me
                        */
                      lastPromise.success(resp)
                  }
              }
          }
        case Failure(exception) =>
      }

    case StartResponseFlow(correlationId, expectedResponseNumber) =>
      if(!responseFlowPool.contains(correlationId))
        {
          val promise = Promise[Seq[WebSocketResponseParsingState]]
          responseFlowPoolPromises += (correlationId -> promise)
          responseFlowPool += (correlationId -> (expectedResponseNumber, Seq[WebSocketResponseParsingState]()))
          sender() ! promise.future
        }

    case SubscribeForEvent(f, clazz) =>
      try{
        var eventActor = createWebSocketEventActor(f, clazz)
        eventActor ! Subscribe
        sender() ! Future.successful(eventActor.path.name)
      }catch {
        case e : Throwable => Future.failed(e)
      }

    case UnSubscribeForEvent(actorName : String) =>
      eventActorPool.get(actorName) match {
        case Some(actoreRef) =>
          actoreRef ! UnSubscribe
          eventActorPool -= actorName
        case None =>
      }

    // Not yet completed, but don't remove
    /*case ConnectionError(exception) =>
      log.error("Connection error from web socket channel", exception)
      self ! StopClient

    case StopClient =>
      log.info("Stopping web socket client")
      self ! PoisonPill
      */

  }

  private def getResponseFromRawResponse(rawResponse : String, throwable: Throwable = null) : Try[Either[WebSocketResponseParsingState, _ <: WebSocketEvent]] =
  {
    try{
      var responseParsingState = new WebSocketResponseParsingState()
      responseParsingState.fromJsonString(rawResponse)
      if(throwable == null) {
        if (responseParsingState.getRequestType() == 1)
        /**
          *
          * I know only the type of the request, it's an UpdateTipEvent.
          * I will publish it and then the client, since it has more information about it than me,
          * can correctly decorate this event with the appropriate data
          */
          Success(Right(UpdateTipEvent(rawResponse, -1, "", "")))
        else
          Success(Left(responseParsingState))
      }else
        Success(Left(responseParsingState))
    }catch {
      case e : Throwable => Failure(e)
    }
  }

  override def receive: Receive = {
    manageRequest orElse
    {
      case a : Any => log.error(getClass.getName + " has received a strange input: " + a)
    }
  }

  def createWebSocketEventActor[E <: WebSocketEvent](f : E => Unit, clazz : Class[E]) =
  {
    var newEventActor = system.actorOf(Props(new WebSocketEventActor(f, clazz)), String.valueOf(System.currentTimeMillis()))
    var name = newEventActor.path.name
    eventActorPool += (name ->  newEventActor)
    newEventActor
  }
}

object WebSocketClient{
  object ReceivableMessages{
    case class SubscribeForEvent[E <: WebSocketEvent](f : E => Unit, clazz : Class[E])
    case class UnSubscribeForEvent(actorName : String)
    case class StartResponseFlow(correlationId : String, expectedResponseNumber : Int)
    case class CompletedResponseFlow(correlationId : String)

    // Not yet completed, but don't remove
    //case class StopClient()
  }
}

object WebSocketClientRef{
  def props(webSocketConfiguration : WebSocketClientSettings)
           (implicit system : ActorSystem, materializer : ActorMaterializer, ec : ExecutionContext): Props =
    Props(new WebSocketClient(webSocketConfiguration))

  def apply(webSocketConfiguration : WebSocketClientSettings)
           (implicit system : ActorSystem, materializer : ActorMaterializer, ec : ExecutionContext): ActorRef =
    system.actorOf(props(webSocketConfiguration))
}

