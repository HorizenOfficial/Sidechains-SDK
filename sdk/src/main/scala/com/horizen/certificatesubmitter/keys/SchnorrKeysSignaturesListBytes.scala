package com.horizen.certificatesubmitter.keys

import com.horizen.schnorrnative.{SchnorrKeysSignaturesList, SchnorrPublicKey, SchnorrSignature}

import java.util

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

object SchnorrKeysSignaturesListBytes{
  def getSchnorrKeysSignaturesList(schnorrKeysSignaturesListBytes: SchnorrKeysSignaturesListBytes): SchnorrKeysSignaturesList = {
    new SchnorrKeysSignaturesList(
      byteArrayToKeysList(schnorrKeysSignaturesListBytes.schnorrSignersPublicKeysBytesList),
      byteArrayToKeysList(schnorrKeysSignaturesListBytes.schnorrMastersPublicKeysBytesList),
      byteArrayToKeysList(schnorrKeysSignaturesListBytes.newSchnorrSignersPublicKeysBytesList),
      byteArrayToKeysList(schnorrKeysSignaturesListBytes.newSchnorrMastersPublicKeysBytesList),
      byteArrayToSignaturesList(schnorrKeysSignaturesListBytes.updatedSigningKeysSkSignatures),
      byteArrayToSignaturesList(schnorrKeysSignaturesListBytes.updatedSigningKeysMkSignatures),
      byteArrayToSignaturesList(schnorrKeysSignaturesListBytes.updatedMasterKeysSkSignatures),
      byteArrayToSignaturesList(schnorrKeysSignaturesListBytes.updatedMasterKeysMkSignatures)
    )
  }

  private def byteArrayToKeysList(schnorrPublicKeysBytesList: Seq[Array[Byte]]): util.List[SchnorrPublicKey] =
    scala.collection.JavaConverters.seqAsJavaList(schnorrPublicKeysBytesList.map(SchnorrPublicKey.deserialize))

  private def byteArrayToSignaturesList(schnorrSignaturesBytesList: Seq[Array[Byte]]): util.List[SchnorrSignature] =
    scala.collection.JavaConverters.seqAsJavaList(schnorrSignaturesBytesList.map(SchnorrSignature.deserialize))
}
