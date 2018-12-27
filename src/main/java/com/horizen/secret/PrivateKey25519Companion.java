package com.horizen.secret;

import com.horizen.proof.ProofOfKnowledge;
import scala.Tuple2;
import scorex.core.transaction.box.Box;

class PrivateKey25519Companion<S extends PrivateKey25519> implements SecretCompanion<S>
{

    @Override
    public boolean owns(S secret, Box<?> box) {
        return false;
    }

    @Override
    public ProofOfKnowledge sign(S secret, byte[] message) {
        return null;
    }

    @Override
    public boolean verify(byte[] message, Object publicImage, Object proof) {
        return false;
    }

    @Override
    public Tuple2<S, Object> generateKeys(byte[] randomSeed) {
        return null;
    }
}