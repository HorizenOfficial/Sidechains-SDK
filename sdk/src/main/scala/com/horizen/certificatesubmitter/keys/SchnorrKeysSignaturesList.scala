package com.horizen.certificatesubmitter.keys

import com.horizen.schnorrnative.{SchnorrPublicKey, SchnorrSignature}

import java.util

case class SchnorrKeysSignaturesList(
                                      signingKeys: java.util.List[SchnorrPublicKey],
                                      masterKeys: java.util.List[SchnorrPublicKey],
                                      updatedSigningKeys: java.util.List[SchnorrPublicKey],
                                      updatedMasterKeys: java.util.List[SchnorrPublicKey],
                                      updatedSigningKeysSkSignatures: java.util.List[SchnorrSignature],
                                      updatedSigningKeysMkSignatures: java.util.List[SchnorrSignature],
                                      updatedMasterKeysSkSignatures: java.util.List[SchnorrSignature],
                                      updatedMasterKeysMkSignatures: java.util.List[SchnorrSignature]
                                    )

object SchnorrKeysSignaturesList {
  def getSchnorrKeysSignaturesList(schnorrKeysSignaturesListBytes: SchnorrKeysSignaturesListBytes): SchnorrKeysSignaturesList = {
    SchnorrKeysSignaturesList(
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