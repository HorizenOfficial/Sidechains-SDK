package com.horizen.mainchain.api

import java.io.{BufferedReader, InputStreamReader}

import com.fasterxml.jackson.databind.ObjectMapper
import com.horizen.SidechainSettings
import com.horizen.utils.BytesUtils

class RpcMainchainNodeApi(val sidechainSettings: SidechainSettings)
  extends MainchainNodeApi
{

  private lazy val isOsWindows = {
    val osname = System.getProperty("os.name", "generic").toLowerCase()
    osname.contains("win")
  }

  private val clientPath = sidechainSettings.websocket.zencliCommandLine + " " +
    (sidechainSettings.genesisData.mcNetwork match {
      case "regtest" => "-regtest "
      case "testnet" => "-testnet "
      case _ => ""
    })

  private def callRpc(params: String) : String = {
    System.out.println(clientPath + " " + params)
    val process = Runtime.getRuntime.exec(clientPath + " " + params)

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
      "'" + parameter + "'"
  }

  private def encloseStringParameter(parameter: String): String = {
    "\"" + parameter + "\""
  }

  override def getSidechainInfo: SidechainInfoResponse = {
    val objectMapper = new ObjectMapper()
    val response = callRpc("getscinfo")

    objectMapper.readValue(response, classOf[SidechainInfoResponse])
  }

  override def sendCertificate(certificateRequest: SendCertificateRequest): SendCertificateResponse = {
    val objectMapper = new ObjectMapper()

    val response = callRpc("send_certificate "
      + encloseStringParameter(BytesUtils.toHexString(certificateRequest.sidechainId)) + " "
      + certificateRequest.epochNumber + " "
      + encloseStringParameter(BytesUtils.toHexString(certificateRequest.endEpochBlockHash)) + " "
      + "\"[]\" " //encloseJsonParameter(objectMapper.writeValueAsString(certificateRequest.backwardTransfers)) + " " // TODO: fix json serialization
      + certificateRequest.fee
      )

    SendCertificateResponse(BytesUtils.fromHexString(response))
  }
}
