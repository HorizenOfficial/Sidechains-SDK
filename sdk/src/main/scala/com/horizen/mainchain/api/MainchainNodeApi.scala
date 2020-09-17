package com.horizen.mainchain.api

trait MainchainNodeApi {
  def sendCertificate(certificateRequest: SendCertificateRequest): SendCertificateResponse
}
