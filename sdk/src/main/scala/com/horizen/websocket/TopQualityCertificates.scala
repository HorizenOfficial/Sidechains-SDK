package com.horizen.websocket

class TopQualityCertificates (val mempoolCertHash: Option[Array[Byte]],
                              val mempoolRawCertificate: Option[Array[Byte]],
                              val mempoolCertQuality: Option[Int],
                              val mempoolCertFee: Option[Double],
                              val chainCertHash: Option[Array[Byte]],
                              val chainRawCertificate: Option[Array[Byte]],
                              val chainCertQuality: Option[Int]) {

}
