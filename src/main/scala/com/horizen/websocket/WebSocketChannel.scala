package com.horizen.websocket

import java.util.concurrent.TimeUnit

import akka.Done
import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{StatusCodes, Uri}
import akka.http.scaladsl.model.ws.{BinaryMessage, Message, TextMessage, WebSocketRequest}
import akka.stream.{ActorMaterializer, OverflowStrategy}
import akka.stream.scaladsl.{Keep, Sink, Source}
import com.horizen.WebSocketClientSettings
import com.horizen.websocket.WebSocketChannel.ReceivableMessages.{ConnectionError, OpenChannel, ReceiveMessage, SendMessage}
import scorex.util.ScorexLogging

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

class WebSocketChannel(webSocketClient :ActorRef, webSocketConfiguration : WebSocketClientSettings)
                          (implicit system : ActorSystem, materializer : ActorMaterializer, ec : ExecutionContext) extends Actor with ScorexLogging {

  private var webSocketRequest : WebSocketRequest = null
  /**
    * Only for simple test case during development.
    * Please, don't remove until the code will be accepted into the remote repository
    */
  //var count = 0

  private def manageMessageRequest: Receive = {
    case OpenChannel =>
      openWebSocketChannel
      /**
        * Only for simple test case during development.
        * Please, don't remove until the code will be accepted into the remote repository
        */
/*      var count = 0
      new Thread(new Runnable {
        override def run(): Unit = {
          while(count<6){
            count = count+1
            log.info("Event count: " + count)
            sendMessage("{\"type\":1,\"height\":265,\"hash\":\"012eca4e7c578a9\",\"block\":\"000000201edc000000\"}")
            Thread.sleep(2000)
          }
        }
      }).start()*/

    case SendMessage(message) =>
      sendMessage(message)
    /**
      * Only for simple test case during development.
      * Please, don't remove until the code will be accepted into the remote repository
      */
/*      if(count==0)
        {
          sendMessage("{\"type\":2,\"height\":50,\"hash\":\"0372229473df1b966945e2b307b86bd856323a54c645ccb91cabd1a49d8f87bf\",\"block\":\"00000028700000000\",\"msgId\":\"1234\"}")
          count = count + 1
        }
      else
        sendMessage("{\"type\":2,\"errorCode\":5,\"message\":\"Invalid parameter\"}")*/
      //sendMessage("{\"type\":2,\"height\":50,\"hash\":\"0372229473df1b966945e2b307b86bd856323a54c645ccb91cabd1a49d8f87bf\",\"block\":\"000000202882536a090581fb98fdfc872791ae81aab1b3c6feace3f01e4766a4a53aa2061ddaa2198c22cca64d114be91b76399170e009d47b0460aefa9309f6a9fec2840000000000000000000000000000000000000000000000000000000000000000c382395d0d0f0f2031001d20d318763d7ce99014d0a0d8faefbd935ac188aba6fed40f4f1c500000240ee086b40114aa019b646b7c9fe79526355128e652deb38bed00fc765b673697869707f00101000000010000000000000000000000000000000000000000000000000000000000000000ffffffff0401320101ffffffff02703d2c44000000001976a914ae8a28f479ed52670d98ee6ffa17c8f39ae5cd4888ac103f55060000000017a914b6863b182a52745bf6d5fb190139a2aa876c08f58700000000\",\"msgId\":\"1234\"}")
      //sendMessage("{\"type\":4,\"height\":201,\"hashes\":[\"08d34596dd7e137f603d6661d867eb083c0592e8333f838478de2ebd3efd8e5a\",\"0e4d418de14c9aeaba0714bc23626bab1fe12001758dd6d4908029ad588b5861\",\"0e812bd816810df31b1cbfdacec59768e3dc668dbd9186c421bc44730e61ecfa\",\"0e760f0f0e47b5155905e155c26ee5af680bca459d1273cf2ba4eaaad4c1ca7d\",\"001c2bca61711e2f74a001283eb3cb60645d0d42586ccb14879afcd68c2ed2f9\",\"028db24dc6985679cdff9ce4648d110236123729760affd48836d26ca5cca7f4\"],\"msgId\":\"1234\"}")
      //sendMessage("{\"type\":3,\"counter\":1,\"height\":51,\"hash\":\"0db07ab4539baf9a3dd4a72c7fe5211090ec6fbca56af11f63b5eee32aaecf06\",\"block\":\"00000020bf878f9da4d1ab1cb9cc45c6543a3256d86bb807b3e24569961bdf739422720340f4002d71faf077aaf093f425e95c876da0f34aecb8cde535c40e9cf86b87f50000000000000000000000000000000000000000000000000000000000000000c382395d0d0f0f200300b94eda34d5e1048a75d419eb1582dda3c14d9a9af0bb304c433928df000024003caf1eb3163d8ce8106728d8c16240a8d736da20fe165639cdca3a285a10f6ac0db5b70101000000010000000000000000000000000000000000000000000000000000000000000000ffffffff0401330101ffffffff02703d2c44000000001976a914ae8a28f479ed52670d98ee6ffa17c8f39ae5cd4888ac103f55060000000017a914b6863b182a52745bf6d5fb190139a2aa876c08f58700000000\",\"msgId\":\"1234\"}")
      //sendMessage("{\"type\":3,\"counter\":2,\"height\":51,\"hash\":\"0a1173bfd0b7ca4bdb2ec94415020ae7aa9104a73ef47aa2adff9dd1059a1adc\",\"block\":\"0000002006cfae2ae3eeb5631ff16aa5bc6fec901021e57f2ca7d43d9aaf9b53b47ab00d1f85dbd9fe1c10f438033f55604a414d8245f2899b3393c5200eb4150bd3e81e0000000000000000000000000000000000000000000000000000000000000000c382395d0c0f0f202900c21aee9851db419bf45ea93da751c6a94fe94b559c966cf49c8f0c7f0000241dc91bf1e4c33a677142e3dc79463e4ebfcb2a476d1f42da7f33cf33405a4d4556d6ddfb0101000000010000000000000000000000000000000000000000000000000000000000000000ffffffff0401340101ffffffff02703d2c44000000001976a914ae8a28f479ed52670d98ee6ffa17c8f39ae5cd4888ac103f55060000000017a914b6863b182a52745bf6d5fb190139a2aa876c08f58700000000\",\"msgId\":\"1234\"}")
      //sendMessage("{\"type\":3,\"counter\":3,\"height\":51,\"hash\":\"0a1173bfd0b7ca4bdb2ec94415020ae7aa9104a73ef47aa2adff9dd1059a1adc\",\"block\":\"0000002006cfae2ae3eeb5631ff16aa5bc6fec901021e57f2ca7d43d9aaf9b53b47ab00d1f85dbd9fe1c10f438033f55604a414d8245f2899b3393c5200eb4150bd3e81e0000000000000000000000000000000000000000000000000000000000000000c382395d0c0f0f202900c21aee9851db419bf45ea93da751c6a94fe94b559c966cf49c8f0c7f0000241dc91bf1e4c33a677142e3dc79463e4ebfcb2a476d1f42da7f33cf33405a4d4556d6ddfb0101000000010000000000000000000000000000000000000000000000000000000000000000ffffffff0401340101ffffffff02703d2c44000000001976a914ae8a28f479ed52670d98ee6ffa17c8f39ae5cd4888ac103f55060000000017a914b6863b182a52745bf6d5fb190139a2aa876c08f58700000000\",\"msgId\":\"1234\"}")
  }

  override def receive: Receive = {
    manageMessageRequest orElse
      {
        case a : Any => log.error(getClass.getName + " has received a strange input: " + a)
      }
  }

  val incoming: Sink[Message, Future[Done]] =
    Sink.foreach[Message] {
      case tm: TextMessage =>
        webSocketClient ! ReceiveMessage(tm.getStrictText)
      case bm: BinaryMessage =>
        // ignore binary messages but drain content to avoid the stream being clogged
        bm.dataStream.runWith(Sink.ignore)
      case _ => log.error("Web socket channel: Unexpected message type")
    }

  def openWebSocketChannel = {
    val future : Future[Unit] = createAndOpenWebSocketChannel(webSocketConfiguration)
    val timeout = FiniteDuration(
      webSocketConfiguration.connectionTimeout,
      TimeUnit.valueOf(webSocketConfiguration.connectionTimeUnit))
    future.onComplete{
      case Success(value) =>
      case Failure(exception) =>
        webSocketClient ! ConnectionError(exception)
    }
    /**
      * Note : if we don't use a barrier, we can have a scenario in which the stream is not ready to process incoming messages.
      * Therefore the message is enqueued but processed later, when the stream will be ready.
     */
    Await.result(future, timeout)
  }

  private def createChannel(conf : WebSocketClientSettings) : Try[WebSocketRequest] = {
    var socketAddress = conf.bindingAddress
    var uri  = Uri.from(
      scheme = "ws",
      host = socketAddress.getHostName,
      port = socketAddress.getPort)
    Try(WebSocketRequest.fromTargetUri(uri))
  }

  private def startQueue (webSocketRequest : WebSocketRequest) =
    Source.queue[Message](Int.MaxValue, OverflowStrategy.backpressure)
      .viaMat(Http().webSocketClientFlow(webSocketRequest))(Keep.both)
      .toMat(incoming)(Keep.both)
      .run()

  private def createAndOpenWebSocketChannel(conf : WebSocketClientSettings): Future[Unit]  = {
    createChannel(conf) match {
      case Success(channel) =>
        try{

          webSocketRequest = channel
          val (upgradeResponse, closed) = startQueue(webSocketRequest)

          upgradeResponse._2.flatMap { upgrade =>
            if (upgrade.response.status == StatusCodes.SwitchingProtocols) {
              log.info(s"Connection established: ${webSocketRequest.uri.toString()}")

              closed.onComplete{
                case Success(value) =>
                  log.info(s"Connection closed: ${webSocketRequest.uri.toString()}")
                case Failure(exception) =>
                  Future.failed(exception)
              }

              Future.successful()

            } else {
              var exception = new RuntimeException(s"Connection failed: ${webSocketRequest.uri.toString()}")
              Future.failed(exception)
            }
          }


        }catch {
          case e : Throwable =>
            Future.failed(e)
        }

      case Failure(exception) =>
        Future.failed(exception)
    }
  }

  private def sendMessage (msg : String) =
    try {
      startQueue(webSocketRequest)._1._1.offer(TextMessage(msg))
    }catch{
      case e : Throwable => webSocketClient ! ReceiveMessage(msg, e)
    }

}

object WebSocketChannel{
  object ReceivableMessages{
    case class SendMessage(message : String)
    case class ReceiveMessage(message : String, throwable : Throwable = null)
    case class OpenChannel()
    case class ConnectionError(throwable : Throwable)
  }
}

object WebSocketChannelRef{
  def props(webSocketClient :ActorRef, webSocketConfiguration : WebSocketClientSettings)
           (implicit system : ActorSystem, materializer : ActorMaterializer, ec : ExecutionContext): Props =
    Props(new WebSocketChannel(webSocketClient, webSocketConfiguration))

  def apply(webSocketClient :ActorRef, webSocketConfiguration : WebSocketClientSettings)
           (implicit system : ActorSystem, materializer : ActorMaterializer, ec : ExecutionContext): ActorRef =
    system.actorOf(props(webSocketClient, webSocketConfiguration))
}

