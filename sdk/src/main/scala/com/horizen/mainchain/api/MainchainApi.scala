package com.horizen.mainchain.api

import com.horizen.mainchain.{CertificateRequest, CertificateResponce, SidechainInfoResponce}

trait MainchainApi {

  def getSidechainInfo : SidechainInfoResponce

  def sendCertificate (certificateRequest: CertificateRequest): CertificateResponce

}
