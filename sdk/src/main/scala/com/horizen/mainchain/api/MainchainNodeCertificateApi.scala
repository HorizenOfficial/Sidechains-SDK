package com.horizen.mainchain.api

import scala.util.Try

trait MainchainNodeCertificateApi {
  def sendCertificate(certificateRequest: SendCertificateRequest): Try[SendCertificateResponse]
}
