package com.horizen.mainchain.api

import scala.util.Try

trait MainchainNodeApi {
  def sendCertificate(certificateRequest: SendCertificateRequest): Try[SendCertificateResponse]
}
