package com.horizen.mainchain.api

import com.horizen.mainchain.SidechainInfoResponce
import com.horizen.mainchain.CertificateRequest
import com.horizen.mainchain.CertificateRequestResponce
import com.horizen.mainchain.RawCertificate
import com.horizen.mainchain.RawCertificateResponce

trait MainchainApi {

  def getSidechainInfo : SidechainInfoResponce

  def sendCertificate (certificateRequest: CertificateRequest): CertificateRequestResponce

  def getRawCertificate(rawCertificate: RawCertificate): RawCertificateResponce

}
