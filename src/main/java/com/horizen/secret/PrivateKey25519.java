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
    public scorex.core.ModifierTypeId secretTypeId() {
        return null; // scorex.core.ModifierTypeId @@ 3.toByte
    }
}
