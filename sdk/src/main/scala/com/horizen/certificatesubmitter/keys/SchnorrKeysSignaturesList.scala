package com.horizen.certificatesubmitter.keys

import com.horizen.schnorrnative.{SchnorrPublicKey, SchnorrSignature}

case class SchnorrKeysSignaturesList(
                                      val signingKeys: Array[SchnorrPublicKey],
                                      val masterKeys: Array[SchnorrPublicKey],
                                      val updatedSigningKeys: Array[SchnorrPublicKey],
                                      val updatedMasterKeys: Array[SchnorrPublicKey],
                                      val updatedSigningKeysSkSignatures: Array[SchnorrSignature],
                                      val updatedSigningKeysMkSignatures: Array[SchnorrSignature],
                                      val updatedMasterKeysSkSignatures: Array[SchnorrSignature],
                                      val updatedMasterKeysMkSignatures: Array[SchnorrSignature]
                                    )