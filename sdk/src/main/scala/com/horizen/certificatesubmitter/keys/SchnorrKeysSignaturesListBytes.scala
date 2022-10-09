package com.horizen.certificatesubmitter.keys

case class SchnorrKeysSignaturesListBytes(
                                      schnorrSignersPublicKeysBytesList: Seq[Array[Byte]],
                                      schnorrMastersPublicKeysBytesList: Seq[Array[Byte]],
                                      newSchnorrSignersPublicKeysBytesList: Seq[Array[Byte]],
                                      newSchnorrMastersPublicKeysBytesList: Seq[Array[Byte]],
                                      updatedSigningKeysSkSignatures: Seq[Array[Byte]],
                                      updatedSigningKeysMkSignatures: Seq[Array[Byte]],
                                      updatedMasterKeysSkSignatures: Seq[Array[Byte]],
                                      updatedMasterKeysMkSignatures: Seq[Array[Byte]],
                                    )
