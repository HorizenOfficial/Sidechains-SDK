package io.horizen.account.proof;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonView;
import io.horizen.account.proposition.AddressProposition;
import io.horizen.account.secret.PrivateKeySecp256k1;
import io.horizen.account.utils.Secp256k1;
import io.horizen.evm.Address;
import io.horizen.proof.ProofOfKnowledge;
import io.horizen.proof.ProofSerializer;
import io.horizen.json.Views;
import io.horizen.utils.BytesUtils;
import java.math.BigInteger;

import static io.horizen.account.utils.EthereumTransactionUtils.trimLeadingZeroFromByteArray;

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

    private static BigInteger secp256k1N= new BigInteger("fffffffffffffffffffffffffffffffebaaedce6af48a03bbfd25e8cd0364141", 16);
    private static BigInteger secp256k1halfN = secp256k1N.divide(BigInteger.TWO);

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

    public static void verifySignatureData(BigInteger v, BigInteger r, BigInteger s) {

        // TODO:
        //  check if we always have real signature also in case of eip155 txes.
        //  In this case we have 27/28 as only possible values
        if (v.bitLength()/8 > Long.BYTES)
            throw new IllegalArgumentException("Too large a v obj passed in signature data");

        if (v.signum() < 0 || r.signum() < 0 || s.signum() < 0) {
            throw new IllegalArgumentException("Negative r/s obj passed in signature data");
        }
        // reject upper range of s values (ECDSA malleability)
        if (s.compareTo(secp256k1halfN) > 0) {
            throw new IllegalArgumentException("Invalid signature s obj passed in signature data");
        }

        if (r.compareTo(secp256k1N) >= 0 || s.compareTo(secp256k1N) >= 0) {
            throw new IllegalArgumentException("Invalid signature r/s obj passed in signature data");
        }

        /*
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


    public SignatureSecp256k1(byte[] v, byte[] r, byte[] s) {

        verifySignatureData(v, r, s);

        this.v = v;
        this.r = r;
        this.s = s;
    }

    public SignatureSecp256k1(BigInteger v, BigInteger r, BigInteger s) {
        var t_v = trimLeadingZeroFromByteArray(v);
        var t_r = trimLeadingZeroFromByteArray(r);
        var t_s = trimLeadingZeroFromByteArray(s);

        verifySignatureData(v, r, s);
        verifySignatureData(t_v, t_r, t_s);

        this.v = t_v;
        this.r = t_r;
        this.s = t_s;
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
                BytesUtils.toHexString(v),
                BytesUtils.toHexString(r),
                BytesUtils.toHexString(s)
        );
    }
}
