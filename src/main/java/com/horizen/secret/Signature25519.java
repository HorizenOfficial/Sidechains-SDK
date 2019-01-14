package com.horizen.secret;

import com.horizen.proof.ProofOfKnowledge;
import com.horizen.proof.ProofSerializer;
import com.horizen.proposition.PublicKey25519Proposition;

import scorex.crypto.signatures.Curve25519;

import java.util.Arrays;
import java.util.Objects;

public class Signature25519 implements ProofOfKnowledge<PrivateKey25519, PublicKey25519Proposition<PrivateKey25519>> {

    static int _signatureLength = Curve25519.SignatureLength();
    byte[] _signatureBytes;

    public byte[] signatureBytes() {
        return Arrays.copyOf(_signatureBytes, _signatureLength);
    }

    public Signature25519 (byte[] signatureBytes) {
        if(signatureBytes.length != _signatureLength)
            throw new IllegalArgumentException(String.format("Incorrect signature length, %d expected, %d found", _signatureLength,
                    signatureBytes.length));

        _signatureBytes = Arrays.copyOf(signatureBytes, _signatureLength);
    }

    @Override
    public boolean isValid(PublicKey25519Proposition proposition, byte[] message) {
        return Curve25519.verify(_signatureBytes, message, proposition.pubKeyBytes());
    }

    @Override
    public byte[] bytes() {
        return serializer().toBytes(this);
    }

    @Override
    public ProofSerializer serializer() {
        ProofSerializer serializer = new Signature25519Serializer();
        return serializer;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Signature25519 that = (Signature25519) o;
        return _signatureLength == that._signatureLength &&
                Arrays.equals(_signatureBytes, that._signatureBytes);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(_signatureLength);
        result = 31 * result + Arrays.hashCode(_signatureBytes);
        return result;
    }
}
