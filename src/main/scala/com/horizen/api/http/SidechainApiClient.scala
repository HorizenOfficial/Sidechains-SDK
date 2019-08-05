package com.horizen.api.http

import com.horizen.SidechainSettings
import scorex.core.api.client.ApiClient
import scorex.core.settings.RESTApiSettings

import java.net.{HttpURLConnection, URL}

import scorex.core.settings.{RESTApiSettings, ScorexSettings}

import scala.io.{Source, StdIn}
import scala.util.{Failure, Success, Try}

/*class SidechainApiClient(settings: RESTApiSettings) {
  private val OkHttpCode = 200

  def executeCommand(command: String): String = {
    if (command.equals("help")) {
      "<method> <url> <data> \n Type quit to stop."
    } else Try {
      val args = command.split(" ")
      val method = args.head.toUpperCase
      val path = args(1)

      val content = if (method.equals("POST")) {
        command.substring((method + " " + path + " ").length())
      } else ""

      // fixme: magic string "http://127.0.0.1:" should come from settings.
      // fixme: Shouldn' we use the address from the bind address instead of 127.0.0.1?
      val url = new URL("http://127.0.0.1:" + settings.bindAddress.getPort + "/" + path)
      val connection = url.openConnection().asInstanceOf[HttpURLConnection]
      connection.setRequestMethod(method)

      if (method.equals("POST")) {
        connection.setDoOutput(true)
        connection.getOutputStream.write(content.getBytes)
        connection.getOutputStream.flush()
        connection.getOutputStream.close()
      }

      val stream = connection.getResponseCode match {
        case OkHttpCode => connection.getInputStream
        case _ => connection.getErrorStream
      }

      val result = Source.fromInputStream(stream).mkString("")
      Try(result)
    }.flatten match {
      case Success(result) => result
      case Failure(e) =>
        s"Problem occurred $e! \n Type help to get a list of commands."
    }
  }
}*/

object SidechainApiClient extends App {

  private val settingsFilename = args.headOption.getOrElse("src/main/resources/settings.conf")
  private val sidechainSettings = SidechainSettings.read(Some(settingsFilename))
  private val sidechainApiClient = new ApiClient(sidechainSettings.scorexSettings.restApi)

  println("Welcome to the Sidechain node command-line client...")
  Iterator.continually(StdIn.readLine()).takeWhile(!_.equals("quit")).foreach { command =>
    println(s"[$command RESULT] " + sidechainApiClient.executeCommand(command))
  }

}
