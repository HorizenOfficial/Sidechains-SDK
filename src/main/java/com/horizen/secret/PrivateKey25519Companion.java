package com.horizen.secret;

import com.horizen.box.Box;
import com.horizen.proof.Signature25519;
import com.horizen.proposition.PublicKey25519Proposition;
import scala.Tuple2;
import scorex.crypto.signatures.Curve25519;

public final class PrivateKey25519Companion implements SecretCompanion<PrivateKey25519, PublicKey25519Proposition, Signature25519>
{
    private static PrivateKey25519Companion companion;

    static {
        companion = new PrivateKey25519Companion();
    }

    private PrivateKey25519Companion() {
        super();
    }

    public static PrivateKey25519Companion getCompanion() {
        return companion;
    }

    @Override
    public boolean owns(PrivateKey25519 secret, Box box) {
        if (box != null && box.proposition() instanceof PublicKey25519Proposition) {
            PublicKey25519Proposition proposition = (PublicKey25519Proposition)box.proposition();
            if(secret.publicImage().equals(proposition))
                return true;
        }
        return false;
    }

    @Override
    public Signature25519 sign(PrivateKey25519 secret, byte[] message) {
        return new Signature25519(Curve25519.sign(secret.privateKeyBytes(), message));
    }

    @Override
    public boolean verify(byte[] message, PublicKey25519Proposition publicImage, Signature25519 proof) {
        return proof.isValid(publicImage, message);
    }

    @Override
    public PrivateKey25519 generateSecret(byte[] randomSeed) {
        Tuple2<byte[], byte[]> keyPair = Curve25519.createKeyPair(randomSeed);
        return new PrivateKey25519(keyPair._1, keyPair._2);
    }
}
