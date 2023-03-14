package com.horizen.certificatesubmitter.keys

import com.horizen.proof.SchnorrProof
import com.horizen.proposition.SchnorrProposition
import com.horizen.utils.BytesUtils

case class SchnorrKeysSignatures(
                                  schnorrSigners: Seq[SchnorrProposition],
                                  schnorrMasters: Seq[SchnorrProposition],
                                  newSchnorrSigners: Seq[SchnorrProposition],
                                  newSchnorrMasters: Seq[SchnorrProposition],
                                  updatedSigningKeysSkSignatures: Seq[Option[SchnorrProof]],
                                  updatedSigningKeysMkSignatures: Seq[Option[SchnorrProof]],
                                  updatedMasterKeysSkSignatures: Seq[Option[SchnorrProof]],
                                  updatedMasterKeysMkSignatures: Seq[Option[SchnorrProof]]
                                ) {
  override def toString: String = {
    s"signers public keys = ${mapFromSchnorrProposition(schnorrSigners)}), " +
      s"masters public keys = ${mapFromSchnorrProposition(schnorrMasters)}, " +
      s"new signers public keys = ${mapFromSchnorrProposition(newSchnorrSigners)}), " +
      s"new masters public keys = ${mapFromSchnorrProposition(newSchnorrMasters)}), " +
      s"updated signers keys signing key signatures = ${mapFromOptionalSignature(updatedSigningKeysSkSignatures)}), " +
      s"updated signers keys master key signatures = ${mapFromOptionalSignature(updatedSigningKeysMkSignatures)}), " +
      s"updated master keys signing key signatures = ${mapFromOptionalSignature(updatedMasterKeysSkSignatures)}), " +
      s"updated master keys master key signatures = ${mapFromOptionalSignature(updatedMasterKeysMkSignatures)})"
  }

  private def mapFromSchnorrProposition(propositions: Seq[SchnorrProposition]): Seq[String] = {
    propositions.map(_.pubKeyBytes()).map(BytesUtils.toHexString)
  }

  private def mapFromOptionalSignature(signatures: Seq[Option[SchnorrProof]]): Seq[String] = {
    signatures.map {
      case Some(k) =>
        BytesUtils.toHexString(k.bytes())
      case None =>
        "None"
    }
  }
}
