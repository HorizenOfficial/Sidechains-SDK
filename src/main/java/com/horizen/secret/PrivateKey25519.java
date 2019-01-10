package com.horizen.secret;

import com.horizen.proposition.PublicKey25519Proposition;
import com.horizen.proof.ProofOfKnowledge;

import scala.Tuple2;


public class PrivateKey25519 implements Secret
{
    // TO DO: change to scorex.crypto.{PublicKey,PrivateKey}
    byte[] _privateKeyBytes;
    byte[] _publicKeyBytes;

    public PrivateKey25519(byte[] privateKeyBytes, byte[] publicKeyBytes)
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
