package com.horizen.mainchain.api

import com.fasterxml.jackson.databind.ObjectMapper
import com.horizen.SidechainSettings
import com.horizen.mainchain.{CertificateRequest, CertificateRequestResponce, RawCertificate, RawCertificateResponce, SidechainInfoResponce}
import com.horizen.utils.BytesUtils

class RpcMainchainApi(val sidechainSettings: SidechainSettings)
  extends MainchainApi
{

  private val clientPath = sidechainSettings.mainchainSettings.path + "src/zen-cli -regtest"

  private def callRpc(params: String) : String = {
    val process = Runtime.getRuntime.exec(clientPath + " " + params)

    val output = process.getOutputStream

    output.toString
  }

  override def getSidechainInfo: SidechainInfoResponce = {
    val objectMapper = new ObjectMapper()
    val responce = callRpc("getscinfo")

    objectMapper.readValue(responce, classOf[SidechainInfoResponce])
  }

  override def sidechainBackwardTransfer(certificateRequest: CertificateRequest): CertificateRequestResponce = {
    val objectMapper = new ObjectMapper()
    val responce = callRpc("sc_bwdtr " + BytesUtils.toHexString(certificateRequest.sidechainId)
      + objectMapper.writeValueAsString(certificateRequest.withdrawalRequests))

    CertificateRequestResponce(BytesUtils.fromHexString(responce))
  }

  override def getRawCertificate(rawCertificate: RawCertificate): RawCertificateResponce = ???
}
