package com.horizen.certificatesubmitter.keys

import com.horizen.schnorrnative.{SchnorrPublicKey, SchnorrSignature, ValidatorKeysUpdatesList}

import java.util

case class SchnorrKeysSignaturesListBytes(
                                      schnorrSignersPublicKeysBytesList: Seq[Array[Byte]],
                                      schnorrMastersPublicKeysBytesList: Seq[Array[Byte]],
                                      newSchnorrSignersPublicKeysBytesList: Seq[Array[Byte]],
                                      newSchnorrMastersPublicKeysBytesList: Seq[Array[Byte]],
                                      updatedSigningKeysSkSignatures: Seq[Option[Array[Byte]]],
                                      updatedSigningKeysMkSignatures: Seq[Option[Array[Byte]]],
                                      updatedMasterKeysSkSignatures: Seq[Option[Array[Byte]]],
                                      updatedMasterKeysMkSignatures: Seq[Option[Array[Byte]]],
                                    )

object SchnorrKeysSignaturesListBytes{
  def getSchnorrKeysSignaturesList(schnorrKeysSignaturesListBytes: SchnorrKeysSignaturesListBytes): ValidatorKeysUpdatesList = {
    new ValidatorKeysUpdatesList(
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

  private def byteArrayToSignaturesList(schnorrSignaturesBytesList: Seq[Option[Array[Byte]]]): util.List[SchnorrSignature] = {
    scala.collection.JavaConverters.seqAsJavaList (schnorrSignaturesBytesList.collect {
      case Some(sig) =>
        SchnorrSignature.deserialize(sig)
      case None =>
        new SchnorrSignature()
    })
  }
}
