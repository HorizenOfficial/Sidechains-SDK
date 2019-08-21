package com.horizen.websocket

import java.net.InetSocketAddress

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import com.horizen.WebSocketClientSettings

import scala.util.{Failure, Success}

/**
  * Only for simple test case during development.
  * Please, don't remove until the code will be accepted into the remote repository
  *
  * This is a simple runnable test case that shows how to use web socket client feature.
  * In future this class will be replaced by test cases into src/main/test...
  */
object WebSocketClientTest extends App{

  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()
  implicit val ec =  system.dispatcher

  var conf = new WebSocketClientSettings(new InetSocketAddress("echo.websocket.org", 80))

  var mcComChannel = new MainchainCommunicationChannel(conf)
  mcComChannel.openCommunicationChannel()

  var mcComChannel2 = new MainchainCommunicationChannel(conf)
  mcComChannel2.openCommunicationChannel()

  try {
/*    var resp_1 = mcComChannel.getBlock(None, Some("0372229473df1b966945e2b307b86bd856323a54c645ccb91cabd1a49d8f87bf"))
      //.getSingleBlock(None, Some("0372229473df1b966945e2b307b86bd856323a54c645ccb91cabd1a49d8f87bf"))
    resp_1 match {
      case Success(value) =>
        println ("Response from mainchain communication channel: " + value.toString)
      case Failure(exception) => println("Response from mainchain communication channel: " + exception.getMessage)
    }*/

/*    var resp_1_2 = mcComChannel2.getBlock(None, Some("0372229473df1b966945e2b307b86bd856323a54c645ccb91cabd1a49d8f87bf"))
      //.getSingleBlock(None, Some("0372229473df1b966945e2b307b86bd856323a54c645ccb91cabd1a49d8f87bf"))
    resp_1_2 match {
      case Success(value) =>
        println ("Response from mainchain communication channel 2: " + value.toString)
      case Failure(exception) => println("Response from mainchain communication channel 2: " + exception.getMessage)
    }

    resp_1 = mcComChannel.getBlock(None, Some("0372229473df1b966945e2b307b86bd856323a54c645ccb91cabd1a49d8f87bf"))
      //.getSingleBlock(None, Some("0372229473df1b966945e2b307b86bd856323a54c645ccb91cabd1a49d8f87bf"))
    resp_1 match {
      case Success(value) =>
        println ("Response from mainchain communication channel: " + value.toString)
      case Failure(exception) => println("Response from mainchain communication channel: " + exception.getMessage)
    }*/

/*    var resp_2 = mcComChannel.getBlocks(3, None, Some("0372229473df1b966945e2b307b86bd856323a54c645ccb91cabd1a49d8f87bf"))
      //.getMultipleBlocks(3, None, Some("0372229473df1b966945e2b307b86bd856323a54c645ccb91cabd1a49d8f87bf"))
    println ("Response from websocket client: " + resp_2.toString)*/

/*    var resp_3 = mcComChannel.getBlockHashes(2, None, Some("0372229473df1b966945e2b307b86bd856323a54c645ccb91cabd1a49d8f87bf"))
      //.getMultipleBlockHashes(2, None, Some("0372229473df1b966945e2b307b86bd856323a54c645ccb91cabd1a49d8f87bf"))
    println ("Response from websocket client: " + resp_3.toString)*/

/*   var actorName_1 = mcComChannel.onUpdateTipEvent({
      event => println("First subscription. Event received: " + event.toString)
    })
    println("First actor created: " + actorName_1)

    var actorName_2 = mcComChannel.onUpdateTipEvent({
      event => println("Second subscription. Event received: " + event.toString)
    })
    println("Second actor created: " + actorName_2)

    Thread.sleep(4000)
    mcComChannel.stopOnUpdateTipEvent(actorName_1)*/

    /*
       Thread.sleep(7000)
       (client ? StopClient).onComplete{
         case Success(value) =>
           println ("Web socket client stopped")
         case Failure(exception) => println(exception)
       }

       client = WebSocketClientRef(conf)
       Thread.sleep(4000)

*/

    println("Test completed...")

  }catch {
    case e : Exception =>
      println(e)
  }
}