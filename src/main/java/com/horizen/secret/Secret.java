package com.horizen.secret;

import scala.Tuple2;

import scorex.crypto.signatures.Curve25519;
//import scorex.crypto.signatures.PrivateKey;
//import scorex.crypto.signatures.PublicKey;

import com.horizen.box.Box;
import com.horizen.proof.ProofOfKnowledge;
import com.horizen.proposition.Proposition;
import com.horizen.proposition.ProofOfKnowledgeProposition;

public interface Secret extends scorex.core.serialization.BytesSerializable
{

    // TO DO: uncomment and fix compiler error with access to scala package object in java interface
    byte secretTypeId();

    int keyLength();

    byte[] publicKeyBytes();

    byte[] privateKeyBytes();

    //Serializer
    @Override
    byte[] bytes();

    @Override
    SecretSerializer serializer();

    //Secret
    <PK extends Secret> ProofOfKnowledgeProposition<PK> publicImage();

    //Companion
    boolean owns(Secret secret, Box<ProofOfKnowledgeProposition<Secret>> box);

    // TO DO: check ProofOfKnowledge usage
    ProofOfKnowledge<Secret,ProofOfKnowledgeProposition<Secret>> sign(Secret secret, byte[] message);

    boolean verify(byte[] message, ProofOfKnowledgeProposition<Secret> publicImage,
                   ProofOfKnowledge<Secret,ProofOfKnowledgeProposition<Secret>> proof);

    //static
    static Tuple2<Secret, ProofOfKnowledgeProposition<Secret>> generateKeys(byte[] randomSeed) {
        return new Tuple2(null, null);
    }
}
