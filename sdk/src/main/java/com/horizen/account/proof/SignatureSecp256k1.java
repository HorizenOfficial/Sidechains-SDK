package com.horizen.account.proof;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.horizen.account.proposition.AddressProposition;
import com.horizen.account.secret.PrivateKeySecp256k1;
import com.horizen.account.utils.Secp256k1;
import com.horizen.proof.ProofOfKnowledge;
import com.horizen.proof.ProofSerializer;
import org.web3j.crypto.Keys;
import org.web3j.crypto.Sign;
import org.web3j.utils.Numeric;

import java.security.SignatureException;
import java.util.Objects;

public final class SignatureSecp256k1 implements ProofOfKnowledge<PrivateKeySecp256k1, AddressProposition> {

    @JsonProperty("v")
    private final byte[] v;

    @JsonProperty("r")
    private final byte[] r;

    @JsonProperty("s")
    private final byte[] s;

    public SignatureSecp256k1(byte[] v, byte[] r, byte[] s) {
        if (v.length > Secp256k1.SIGNATURE_V_MAXSIZE ||
                r.length != Secp256k1.SIGNATURE_RS_SIZE ||
                s.length != Secp256k1.SIGNATURE_RS_SIZE) {
            throw new IllegalArgumentException(String.format(
                    "Incorrect signature data: v=%d (expected at most %d); r=%d (expected %d); s=%d (expected %d)",
                    v.length, Secp256k1.SIGNATURE_V_MAXSIZE,
                    r.length, Secp256k1.SIGNATURE_RS_SIZE,
                    s.length, Secp256k1.SIGNATURE_RS_SIZE
            ));
        }
        this.v = v;
        this.r = r;
        this.s = s;
    }

    public SignatureSecp256k1(Sign.SignatureData signature) {
        this(signature.getV(), signature.getR(), signature.getS());
    }

    @Override
    public boolean isValid(AddressProposition proposition, byte[] message) {
        try {
            final var signature = new Sign.SignatureData(v, r, s);
            // verify signature validity for the given message
            final var signingAddress = Keys.getAddress(Sign.signedMessageToKey(message, signature));
            // verify that the signature was created with the expected address
            return Objects.equals(signingAddress, Numeric.toHexStringNoPrefix(proposition.address()));
        } catch (SignatureException e) {
            return false;
        }
    }

    @Override
    public ProofSerializer serializer() {
        return SignatureSecp256k1Serializer.getSerializer();
    }

    public byte[] getV() {
        return v;
    }

    public byte[] getR() {
        return r;
    }

    public byte[] getS() {
        return s;
    }

    @Override
    public String toString() {
        return String.format(
                "SignatureSecp256k1{v=%s, r=%s, s=%s}",
                Numeric.toHexString(v),
                Numeric.toHexString(r),
                Numeric.toHexString(s)
        );
    }
}
