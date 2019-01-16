package com.horizen.secret;

import scala.Tuple2;

import scorex.crypto.signatures.Curve25519;
//import scorex.crypto.signatures.PrivateKey;
//import scorex.crypto.signatures.PublicKey;

import com.horizen.box.Box;
import com.horizen.proof.ProofOfKnowledge;
import com.horizen.proposition.Proposition;
import com.horizen.proposition.ProofOfKnowledgeProposition;

public interface Secret<S extends Secret,
        PKP extends ProofOfKnowledgeProposition<S>,
        PK extends ProofOfKnowledge<S, PKP>> extends scorex.core.serialization.BytesSerializable
{

    // TO DO: uncomment and fix compiler error with access to scala package object in java interface
    byte secretTypeId();

    //Serializer
    @Override
    byte[] bytes();

    @Override
    SecretSerializer serializer();

    //Secret
    PKP publicImage();

    //Companion
//    boolean owns(S secret, Box<PKP> box);
    boolean owns(Box<PKP> box);

    // TO DO: check ProofOfKnowledge usage
//    PK sign(S secret, byte[] message);
    PK sign(byte[] message);
}
