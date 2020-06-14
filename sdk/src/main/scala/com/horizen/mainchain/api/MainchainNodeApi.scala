package com.horizen.mainchain.api

trait MainchainNodeApi {

  def getSidechainInfo: SidechainInfoResponse

  def sendCertificate(certificateRequest: SendCertificateRequest): SendCertificateResponse

}
