package com.horizen.account.proof;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonView;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.horizen.account.proposition.AddressProposition;
import com.horizen.account.secret.PrivateKeySecp256k1;
import com.horizen.account.utils.Secp256k1;
import io.horizen.evm.Address;
import com.horizen.serialization.HexNoPrefixBigIntegerSerializer;
import com.horizen.proof.ProofOfKnowledge;
import com.horizen.proof.ProofSerializer;
import com.horizen.serialization.Views;

import java.math.BigInteger;

@JsonView(Views.Default.class)
public final class SignatureSecp256k1 implements ProofOfKnowledge<PrivateKeySecp256k1, AddressProposition> {

    @JsonProperty("v")
    @JsonSerialize(using = HexNoPrefixBigIntegerSerializer.class)
    private final BigInteger v;

    @JsonProperty("r")
    @JsonSerialize(using = HexNoPrefixBigIntegerSerializer.class)
    private final BigInteger r;

    @JsonProperty("s")
    @JsonSerialize(using = HexNoPrefixBigIntegerSerializer.class)
    private final BigInteger s;

    public static final BigInteger secp256k1N = new BigInteger("fffffffffffffffffffffffffffffffebaaedce6af48a03bbfd25e8cd0364141", 16);
    public static final BigInteger secp256k1halfN = secp256k1N.divide(BigInteger.TWO);
    // byte string is: 7fffffffffffffffffffffffffffffff5d576e7357a4501ddfe92f46681b20a0

     public static void verifySignatureData(BigInteger v, BigInteger r, BigInteger s) {

        if (v == null || r == null || s == null) {
            throw new IllegalArgumentException("Null obj passed in signature data: " +
                    "v=" + ((v == null) ? "NULL" : v.toString(16)) +
            ", r=" + ((r == null) ? "NULL" : r.toString(16)) +
            ", s=" + ((s == null) ? "NULL" : s.toString(16))  );
        }

        if (v.signum() <= 0 || r.signum() <= 0 || s.signum() <= 0) {
            throw new IllegalArgumentException("Non positive v/r/s obj passed in signature data");
        }

        // a positive value should fit in 8 bits
        if (v.bitLength() > 8)
           throw new IllegalArgumentException("Too large a v obj passed in signature data");

        int v_val = v.intValueExact();
        if (v_val != 27 && v_val != 28) {
            throw new IllegalArgumentException("Invalid v obj passed in signature data: " + v_val);
        }

        // reject upper range of s values (ECDSA malleability)
        if (s.compareTo(secp256k1halfN) > 0) { // must be lesser or equal
            throw new IllegalArgumentException("Invalid s obj passed in signature data");
        }

        // reject upper range for r values too (would be a theoretical limit also for s but s has a lower upper value
        // already checked above
        if (r.compareTo(secp256k1N) >= 0) { // must be lesser
            throw new IllegalArgumentException("Invalid signature r/s obj passed in signature data");
        }

        /* Geth code:
        // ValidateSignatureValues verifies whether the signature values are valid with
        // the given chain rules. The v value is assumed to be either 0 or 1.
        func ValidateSignatureValues(v byte, r, s *big.Int, homestead bool) bool {
            if r.Cmp(common.Big1) < 0 || s.Cmp(common.Big1) < 0 {
                return false
            }
            // reject upper range of s values (ECDSA malleability)
            // see discussion in secp256k1/libsecp256k1/include/secp256k1.h
            if homestead && s.Cmp(secp256k1halfN) > 0 {
                return false
            }
            // Frontier: allow s to be in full N range
            return r.Cmp(secp256k1N) < 0 && s.Cmp(secp256k1N) < 0 && (v == 0 || v == 1)
        }

        var (
            secp256k1N, _  = new(big.Int).SetString("fffffffffffffffffffffffffffffffebaaedce6af48a03bbfd25e8cd0364141", 16)
            secp256k1halfN = new(big.Int).Div(secp256k1N, big.NewInt(2))
        )
        */
    }

    public SignatureSecp256k1(BigInteger v_in, BigInteger r_in, BigInteger s_in) {
        verifySignatureData(v_in, r_in, s_in);
        this.v = v_in;
        this.r = r_in;
        this.s = s_in;
    }

    @Override
    public boolean isValid(AddressProposition proposition, byte[] message) {
        return Secp256k1.verify(message, v, r, s, proposition.address().toBytes());
    }

    public boolean isValid(Address address, byte[] message) {
        return Secp256k1.verify(message, v, r, s, address.toBytes());
    }

    @Override
    public ProofSerializer serializer() {
        return SignatureSecp256k1Serializer.getSerializer();
    }

    public BigInteger getV() {
        return v;
    }

    public BigInteger getR() {
        return r;
    }

    public BigInteger getS() {
        return s;
    }

    @Override
    public String toString() {
        return String.format(
                "SignatureSecp256k1{v=%s, r=%s, s=%s}",
                v.toString(16),
                r.toString(16),
                s.toString(16)
        );
    }
}
