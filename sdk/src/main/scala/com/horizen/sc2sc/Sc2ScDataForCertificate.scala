package com.horizen.sc2sc

case class Sc2ScDataForCertificate(messagesTreeRoot: Array[Byte],
                                   previousTopQualityCertificateHash: Option[Array[Byte]])
