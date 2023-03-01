package io.horizen.api.http

import io.horizen.{SidechainSettings, SidechainSettingsReader}
import sparkz.core.api.client.ApiClient

import scala.io.StdIn


object SidechainApiClient extends App {
  private val settingsFilename = args.headOption.getOrElse(throw new Exception("Configuration file is missing."))
  private val sidechainSettings = SidechainSettingsReader.read(settingsFilename, None)
  private val sidechainApiClient = new ApiClient(sidechainSettings.sparkzSettings.restApi)

  println("Welcome to the Sidechain node command-line client...")
  Iterator.continually(StdIn.readLine()).takeWhile(!_.equals("quit")).foreach { command =>
    println(s"[$command RESULT] " + sidechainApiClient.executeCommand(command + " "))
  }

}
