package io.horizen.mainchain.api

import io.horizen.websocket.client.TopQualityCertificates

import scala.util.Try

trait MainchainNodeCertificateApi {
  def sendCertificate(certificateRequest: SendCertificateRequest): Try[SendCertificateResponse]

  // Get information about best certificates in mempool and chain
  // scId must be send in BE format
  def getTopQualityCertificates(scId: String): Try[TopQualityCertificates]
}
