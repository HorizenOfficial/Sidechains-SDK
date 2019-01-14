package com.horizen.secret;

import com.horizen.box.Box;
import com.horizen.box.PublicKey25519NoncedBox;
import com.horizen.proof.ProofOfKnowledge;
import com.horizen.proposition.ProofOfKnowledgeProposition;
import com.horizen.proposition.PublicKey25519Proposition;
import scala.Tuple2;

public class PrivateKey25519Companion<S extends PrivateKey25519, P extends PublicKey25519Proposition<S>, PR extends ProofOfKnowledge<S,P>, B extends PublicKey25519NoncedBox<P>> implements SecretCompanion<S, P, PR, B>
{

    @Override
    public boolean owns(S secret, B box) {
        return false;
    }

    @Override
    public PR sign(S secret, byte[] message) {
        return null;
    }

    @Override
    public boolean verify(byte[] message, P publicImage, PR proof) {
        return false;
    }

    @Override
    public Tuple2<S, P> generateKeys(byte[] randomSeed) {
        return null;
    }
}