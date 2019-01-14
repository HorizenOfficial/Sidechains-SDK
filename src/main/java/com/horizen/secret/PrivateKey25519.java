package com.horizen.secret;

import com.horizen.box.Box;
import com.horizen.proposition.ProofOfKnowledgeProposition;
import com.horizen.proposition.PublicKey25519Proposition;
import com.horizen.proof.ProofOfKnowledge;

import scala.Tuple2;
import scorex.crypto.signatures.Curve25519;

import java.util.Arrays;
import java.util.Objects;


public class PrivateKey25519 implements Secret
{
    // TO DO: change to scorex.crypto.{PublicKey,PrivateKey}
    static int _keyLength = Curve25519.KeyLength();
    byte[] _privateKeyBytes;
    byte[] _publicKeyBytes;

    public PrivateKey25519(byte[] privateKeyBytes, byte[] publicKeyBytes)
    {
        if(privateKeyBytes.length != _keyLength)
            throw new IllegalArgumentException(String.format("Incorrect pubKey length, %d expected, %d found", _keyLength,
                    privateKeyBytes.length));
        if(publicKeyBytes.length != _keyLength)
            throw new IllegalArgumentException(String.format("Incorrect pubKey length, %d expected, %d found", _keyLength,
                    publicKeyBytes.length));

        _privateKeyBytes = Arrays.copyOf(privateKeyBytes, _keyLength);
        _publicKeyBytes = Arrays.copyOf(publicKeyBytes, _keyLength);
    }

    @Override
    public byte secretTypeId() {
        return 0;
    }

    @Override
    public int keyLength() {
        return _keyLength;
    }

    @Override
    public byte[] publicKeyBytes() {
        return Arrays.copyOf(_publicKeyBytes, _keyLength);
    }

    @Override
    public byte[] privateKeyBytes() {
        return Arrays.copyOf(_privateKeyBytes, _keyLength);
    }

    @Override
    public byte[] bytes() {
        return serializer().toBytes(this);
    }

    @Override
    public SecretSerializer serializer() {
        return new PrivateKey25519Serializer();
    }

    @Override
    public <PK extends Secret> ProofOfKnowledgeProposition<PK> publicImage() {
        PublicKey25519Proposition p = new PublicKey25519Proposition<PrivateKey25519>(_publicKeyBytes);
        return p;
    }

    @Override
    public boolean owns(Secret secret, Box<ProofOfKnowledgeProposition<Secret>> box) {
        if (box.proposition() instanceof PublicKey25519Proposition &&
                java.util.Arrays.equals(secret.publicKeyBytes(), ((PublicKey25519Proposition) box.proposition()).pubKeyBytes()))
            return true;
        return false;
    }

    @Override
    public ProofOfKnowledge<Secret, ProofOfKnowledgeProposition<Secret>> sign(Secret secret, byte[] message) {
        ProofOfKnowledge signature = new Signature25519(Curve25519.sign(secret.privateKeyBytes(), message));
        return signature;
    }


    //TODO: check types
    @Override
    public boolean verify(byte[] message, ProofOfKnowledgeProposition<Secret> publicImage,
                          ProofOfKnowledge<Secret, ProofOfKnowledgeProposition<Secret>> proof) {
        return Curve25519.verify(proof.bytes(), message, publicImage.bytes());
    }

    public static Tuple2<Secret, ProofOfKnowledgeProposition<Secret>> generateKeys(byte[] randomSeed) {
        Tuple2<byte[], byte[]> keyPair = Curve25519.createKeyPair(randomSeed);
        PrivateKey25519 secret = new PrivateKey25519(keyPair._1, keyPair._2);
        return new Tuple2<Secret, ProofOfKnowledgeProposition<Secret>>(secret, secret.publicImage());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PrivateKey25519 that = (PrivateKey25519) o;
        return _keyLength == that._keyLength &&
                Arrays.equals(_privateKeyBytes, that._privateKeyBytes) &&
                Arrays.equals(_publicKeyBytes, that._publicKeyBytes);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(_keyLength);
        result = 31 * result + Arrays.hashCode(_privateKeyBytes);
        result = 31 * result + Arrays.hashCode(_publicKeyBytes);
        return result;
    }
}
