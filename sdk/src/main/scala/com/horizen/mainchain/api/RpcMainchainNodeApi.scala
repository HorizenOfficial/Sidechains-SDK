package com.horizen.mainchain.api

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

  private val clientPath = sidechainSettings.websocket.zencliCommandLine +
    sidechainSettings.genesisData.mcNetwork match {
    case "regtest" => "-regtest "
    case "testnet" => "-testnet "
    case _ => ""
  }

  private def callRpc(params: String) : String = {
    val process = Runtime.getRuntime.exec(clientPath + " " + params)

    val output = process.getOutputStream

    output.toString
  }

  private def encloseJsonParameter(parameter: String): String = {
    if (isOsWindows)
      "\"" + parameter.replace("\"", "\\\"") + "\""
    else
      "'" + parameter + "'"
  }

  private def encloseStringParameter(parameter: String): String ={
    "\"" + parameter + "\""
  }

  override def getSidechainInfo: SidechainInfoResponse = {
    val objectMapper = new ObjectMapper()
    val response = callRpc("getscinfo")

    objectMapper.readValue(response, classOf[SidechainInfoResponse])
  }

  override def sendCertificate(certificateRequest: SendCertificateRequest): SendCertificateResponse = {
    val objectMapper = new ObjectMapper()
    val feeParam: String = if (!certificateRequest.subtractFeeFromAmount) {
        " false " + certificateRequest.fee
    } else ""
    val response = callRpc("send_certificate "
      + encloseStringParameter(BytesUtils.toHexString(certificateRequest.sidechainId)) + " "
      + certificateRequest.epochNumber + " "
      + encloseStringParameter(BytesUtils.toHexString(certificateRequest.endEpochBlockHash))
      + encloseJsonParameter(objectMapper.writeValueAsString(certificateRequest.backwardTransfers))
      + feeParam
      )

    SendCertificateResponse(BytesUtils.fromHexString(response))
  }
}
