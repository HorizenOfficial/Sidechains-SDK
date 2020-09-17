package com.horizen.mainchain.api

import java.io.{BufferedReader, InputStreamReader}
import java.util

import com.fasterxml.jackson.databind.{ObjectMapper, SerializationFeature}
import com.horizen.SidechainSettings
import com.horizen.serialization.ApplicationJsonSerializer
import com.horizen.utils.BytesUtils
import scala.collection.JavaConverters._

class RpcMainchainNodeApi(val sidechainSettings: SidechainSettings)
  extends MainchainNodeApi
{

  private lazy val isOsWindows = {
    val osname = System.getProperty("os.name", "generic").toLowerCase()
    osname.contains("win")
  }

  private def zenCliCommand: util.ArrayList[String] = {
    val command = new util.ArrayList[String]()
    command.add(sidechainSettings.websocket.zencliCommandLine)
    sidechainSettings.genesisData.mcNetwork match {
      case "regtest" => command.add("-regtest")
      case "testnet" => command.add("-testnet")
      case _ =>
    }
    command.addAll(sidechainSettings.websocket.zencliCommandLineArguments.getOrElse(Seq()).asJava)
    command
  }

  private def callRpc(pb: ProcessBuilder) : String = {
    System.out.println(pb.command())
    val process = pb.start()

    val stdInput: BufferedReader = new BufferedReader(new InputStreamReader(process.getInputStream))

    val stdError: BufferedReader = new BufferedReader(new InputStreamReader(process.getErrorStream))

    val error = stdError.readLine()

    if(error != null)
      throw new IllegalStateException("Error: " + error)

    stdInput.readLine
  }

  private def encloseJsonParameter(parameter: String): String = {
    if (isOsWindows)
      "\"" + parameter.replace("\"", "\\\"") + "\""
    else
      parameter
  }

  private def encloseStringParameter(parameter: String): String = {
    "\"" + parameter + "\""
  }

  // can be removed
  def getSidechainInfo: SidechainInfoResponse = {
    val objectMapper = new ObjectMapper()
    val response = callRpc(new ProcessBuilder("getscinfo"))

    objectMapper.readValue(response, classOf[SidechainInfoResponse])
  }

  override def sendCertificate(certificateRequest: SendCertificateRequest): SendCertificateResponse = {
    val serializer = ApplicationJsonSerializer.getInstance() // TODO: maybe it's better to construct object mapper from scratch
    serializer.setDefaultConfiguration()
    val objectMapper = serializer.getObjectMapper
    objectMapper.disable(SerializationFeature.INDENT_OUTPUT)


    val command = zenCliCommand
    command.add("send_certificate")
    command.add(BytesUtils.toHexString(certificateRequest.sidechainId))
    command.add(certificateRequest.epochNumber.toString)
    command.add(certificateRequest.quality.toString)
    command.add(BytesUtils.toHexString(certificateRequest.endEpochBlockHash))
    command.add(BytesUtils.toHexString(certificateRequest.proofBytes))
    command.add(encloseJsonParameter(objectMapper.writeValueAsString(certificateRequest.backwardTransfers)))
    command.add(certificateRequest.fee.toString)

    val pb = new ProcessBuilder(command)

    val response = callRpc(pb)

    SendCertificateResponse(BytesUtils.fromHexString(response))
  }
}
