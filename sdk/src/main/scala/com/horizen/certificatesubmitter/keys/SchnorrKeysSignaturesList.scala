package com.horizen.certificatesubmitter.keys

import com.horizen.schnorrnative.{SchnorrPublicKey, SchnorrSignature}

case class SchnorrKeysSignaturesList(
                                      val signingKeys: java.util.List[SchnorrPublicKey],
                                      val masterKeys: java.util.List[SchnorrPublicKey],
                                      val updatedSigningKeys: java.util.List[SchnorrPublicKey],
                                      val updatedMasterKeys: java.util.List[SchnorrPublicKey],
                                      val updatedSigningKeysSkSignatures: java.util.List[SchnorrSignature],
                                      val updatedSigningKeysMkSignatures: java.util.List[SchnorrSignature],
                                      val updatedMasterKeysSkSignatures: java.util.List[SchnorrSignature],
                                      val updatedMasterKeysMkSignatures: java.util.List[SchnorrSignature]
                                    )