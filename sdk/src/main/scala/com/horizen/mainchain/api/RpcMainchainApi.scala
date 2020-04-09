package com.horizen.mainchain.api

import com.fasterxml.jackson.databind.ObjectMapper
import com.horizen.SidechainSettings
import com.horizen.mainchain.{CertificateRequest, CertificateResponce, SidechainInfoResponce}
import com.horizen.utils.BytesUtils

class RpcMainchainApi(val sidechainSettings: SidechainSettings)
  extends MainchainApi
{

  private lazy val os = {
    val osname = System.getProperty("os.name", "generic").toLowerCase()
    if (osname.contains("win"))
      0
    else
      1
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
    if (os == 0)
      "\"" + parameter.replace("\"", "\\\"") + "\""
    else
      "'" + parameter + "'"
  }

  private def enclosStringParameter(parameter: String): String ={
    "\"" + parameter + "\""
  }

  override def getSidechainInfo: SidechainInfoResponce = {
    val objectMapper = new ObjectMapper()
    val responce = callRpc("getscinfo")

    objectMapper.readValue(responce, classOf[SidechainInfoResponce])
  }

  override def sendCertificate(certificateRequest: CertificateRequest): CertificateResponce = {
    val objectMapper = new ObjectMapper()
    val feeParam: String = if (!certificateRequest.subtractFeeFromAmount) {
        " false " + certificateRequest.fee
    } else ""
    val responce = callRpc("send_certificate "
      + enclosStringParameter(BytesUtils.toHexString(certificateRequest.sidechainId)) + " "
      + certificateRequest.epochNumber + " "
      + enclosStringParameter(BytesUtils.toHexString(certificateRequest.endEpochBlockHash))
      + encloseJsonParameter(objectMapper.writeValueAsString(certificateRequest.withdrawalRequests))
      + feeParam
      )

    CertificateResponce(BytesUtils.fromHexString(responce))
  }
}
