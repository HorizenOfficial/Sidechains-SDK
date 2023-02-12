package com.horizen.account.proof;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonView;
import com.horizen.account.proposition.AddressProposition;
import com.horizen.account.secret.PrivateKeySecp256k1;
import com.horizen.account.utils.Secp256k1;
import com.horizen.evm.utils.Address;
import com.horizen.proof.ProofOfKnowledge;
import com.horizen.proof.ProofSerializer;
import com.horizen.serialization.Views;
import com.horizen.utils.BytesUtils;

@JsonView(Views.Default.class)
public final class SignatureSecp256k1 implements ProofOfKnowledge<PrivateKeySecp256k1, AddressProposition> {

    @JsonProperty("v")
    private final byte[] v;

    @JsonProperty("r")
    private final byte[] r;

    @JsonProperty("s")
    private final byte[] s;

    private static boolean checkSignatureDataSizes(byte[] v, byte[] r, byte[] s) {
        return (v.length > 0 && v.length <= Secp256k1.SIGNATURE_V_MAXSIZE) &&
                (r.length == Secp256k1.SIGNATURE_RS_SIZE && s.length == Secp256k1.SIGNATURE_RS_SIZE);
    }

    public static void verifySignatureData(byte[] v, byte[] r, byte[] s) {
        if (v == null || r == null || s == null)
            throw new IllegalArgumentException("Null v/r/s obj passed in signature data");
        if  (!checkSignatureDataSizes(v, r, s)) {
            throw new IllegalArgumentException(String.format(
                    "Incorrect signature length: v=%d (expected 0<v<=%d); r/s==%d/%d (expected %d/%d)",
                    v.length, Secp256k1.SIGNATURE_V_MAXSIZE,
                    r.length, s.length,
                    Secp256k1.SIGNATURE_RS_SIZE, Secp256k1.SIGNATURE_RS_SIZE
            ));
        }
    }

    /**
     * V, R and S are terms of Ethereum signature
     * @param v recovery id
     * @param r first part of signature (bytes 0 to 31 inclusively)
     * @param s second part of signature (bytes 32 to 63 inclusively)
     */
    public SignatureSecp256k1(byte[] v, byte[] r, byte[] s) {
        verifySignatureData(v, r, s);

        this.v = v;
        this.r = r;
        this.s = s;
    }

    @Override
    public boolean isValid(AddressProposition proposition, byte[] message) {
        return Secp256k1.verify(message, this.v, this.r, this.s, proposition.address().toBytes());
    }

    public boolean isValid(Address address, byte[] message) {
        return Secp256k1.verify(message, this.v, this.r, this.s, address.toBytes());
    }

    @Override
    public ProofSerializer serializer() {
        return SignatureSecp256k1Serializer.getSerializer();
    }

    /**
     * @return recovery id
     */
    public byte[] getV() {
        return v;
    }

    /**
     * @return first part of signature (bytes 0 to 31 inclusively)
     */
    public byte[] getR() {
        return r;
    }

    /**
     * @return second part of signature (bytes 32 to 63 inclusively)
     */
    public byte[] getS() {
        return s;
    }

    @Override
    public String toString() {
        return String.format(
                "SignatureSecp256k1{v=%s, r=%s, s=%s}",
                BytesUtils.toHexString(v),
                BytesUtils.toHexString(r),
                BytesUtils.toHexString(s)
        );
    }
}
