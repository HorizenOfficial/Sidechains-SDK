package com.horizen.proof;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonView;
import com.horizen.proposition.PublicKey25519Proposition;
import com.horizen.secret.PrivateKey25519;
import com.horizen.serialization.Views;
import com.horizen.utils.Ed25519;

import java.util.Arrays;
import java.util.Objects;

@JsonView(Views.Default.class)
public final class Signature25519
        implements ProofOfKnowledge<PrivateKey25519, PublicKey25519Proposition> {

    public static int SIGNATURE_LENGTH = Ed25519.signatureLength();

    @JsonProperty("signature")
    byte[] _signatureBytes;

    public Signature25519(byte[] signatureBytes) {
        if (signatureBytes.length != SIGNATURE_LENGTH)
            throw new IllegalArgumentException(String.format("Incorrect signature length, %d expected, %d found", SIGNATURE_LENGTH,
                    signatureBytes.length));

        _signatureBytes = Arrays.copyOf(signatureBytes, SIGNATURE_LENGTH);
    }

    @Override
    public boolean isValid(PublicKey25519Proposition proposition, byte[] message) {
        return Ed25519.verify(_signatureBytes, message, proposition.pubKeyBytes());
    }

    @Override
    public byte[] bytes() {
        return Arrays.copyOf(_signatureBytes, SIGNATURE_LENGTH);
    }

    @Override
    public ProofSerializer serializer() {
        return Signature25519Serializer.getSerializer();
    }

    public static Signature25519 parseBytes(byte[] bytes) {
        return new Signature25519(bytes);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Signature25519 that = (Signature25519) o;
        return SIGNATURE_LENGTH == that.SIGNATURE_LENGTH &&
                Arrays.equals(_signatureBytes, that._signatureBytes);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(SIGNATURE_LENGTH);
        result = 31 * result + Arrays.hashCode(_signatureBytes);
        return result;
    }

}
