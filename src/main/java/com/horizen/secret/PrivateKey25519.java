package com.horizen.secret;

import com.horizen.proposition.PublicKey25519Proposition;
import com.horizen.proof.ProofOfKnowledge;

import scala.Tuple2;
import scorex.core.transaction.box.Box;

import java.security.PrivateKey;
import java.security.PublicKey;

public class PrivateKey25519 implements Secret
{
    // TO DO: change to scorex.crypto.{PublicKey,PrivateKey}
    PrivateKey _privateKeyBytes;
    PublicKey _publicKeyBytes;

    public PrivateKey25519(PrivateKey privateKeyBytes, PublicKey publicKeyBytes)
    {
        // TO DO: require check
        _privateKeyBytes = privateKeyBytes;
        _publicKeyBytes = publicKeyBytes;
    }

    @Override
    public PrivateKey25519Companion companion() {
        return new PrivateKey25519Companion();
    }

    @Override
    public PrivateKey25519 instance() {
        return this;
    }

    @Override
    public PublicKey25519Proposition publicImage() {
        return new PublicKey25519Proposition(_publicKeyBytes);
    }

    @Override
    public byte[] bytes() {
        return serializer().toBytes(this);
    }

    @Override
    public PrivateKey25519Serializer serializer() {
        return new PrivateKey25519Serializer();
    }

    @Override
    public byte secretTypeId() {
        return 3; // scorex.core.ModifierTypeId @@ 3.toByte
    }
}


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