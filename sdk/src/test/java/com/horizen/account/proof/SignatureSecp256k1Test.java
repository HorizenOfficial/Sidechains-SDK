package com.horizen.account.proof;

import com.horizen.account.proposition.AddressProposition;
import com.horizen.account.secret.PrivateKeySecp256k1;
import com.horizen.account.secret.PrivateKeySecp256k1Creator;
import org.junit.Before;
import org.junit.Test;
import org.web3j.utils.Numeric;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;

import static com.horizen.account.proof.SignatureSecp256k1.secp256k1N;
import static com.horizen.account.proof.SignatureSecp256k1.secp256k1halfN;
import static org.junit.Assert.*;

public class SignatureSecp256k1Test {
    SignatureSecp256k1 signatureSecp256k1;
    byte[] message;
    AddressProposition addressProposition;

    @Before
    public void BeforeEachTest() {
        String payload = "This is string to sign";
        message = payload.getBytes(StandardCharsets.UTF_8);

        // Create a key and generate the signature
        PrivateKeySecp256k1 privateKey = PrivateKeySecp256k1Creator.getInstance().generateSecret("sigtest".getBytes(StandardCharsets.UTF_8));
        signatureSecp256k1 = privateKey.sign(message);
        addressProposition = privateKey.publicImage();
    }

    @Test
    public void signatureSecp256k1Test() {
        // Test 1: Illegal Argument Exception while creating new PrivateKeySecp256k1 expected
        assertThrows(IllegalArgumentException.class, () -> new SignatureSecp256k1(signatureSecp256k1.getV(), signatureSecp256k1.getR(), null));

        // Test 2: Illegal Argument Exception while creating new PrivateKeySecp256k1 expected
        assertThrows(IllegalArgumentException.class, () -> new SignatureSecp256k1(signatureSecp256k1.getV(), null, signatureSecp256k1.getS()));

        // Test 3: Illegal Argument Exception while creating new PrivateKeySecp256k1 expected
        assertThrows(IllegalArgumentException.class, () -> new SignatureSecp256k1(null, signatureSecp256k1.getR(), signatureSecp256k1.getS()));

        // Test 4: Illegal Argument Exception while creating new PrivateKeySecp256k1 expected
        assertThrows(IllegalArgumentException.class, () -> new SignatureSecp256k1(
                new BigInteger(1, new byte[20]), signatureSecp256k1.getR(), signatureSecp256k1.getS()));

        // Test 5: Returns signature data string correctly
        assertEquals(String.format("SignatureSecp256k1{v=%s, r=%s, s=%s}", Numeric.toHexStringNoPrefix(signatureSecp256k1.getV()), Numeric.toHexStringNoPrefix(signatureSecp256k1.getR()), Numeric.toHexStringNoPrefix(signatureSecp256k1.getS())), signatureSecp256k1.toString());

        // Test 6: Returns false because of invalid signature
        AddressProposition newAddressProposition = PrivateKeySecp256k1Creator.getInstance().generateSecret("sigtest_other".getBytes()).publicImage();
        assertFalse(signatureSecp256k1.isValid(newAddressProposition, message));

        // Test 7: Returns true for valid signature
        assertTrue(signatureSecp256k1.isValid(addressProposition, message));
    }


    private void check(boolean expected, BigInteger v, BigInteger r, BigInteger s) {
        if (expected) {
            new SignatureSecp256k1(v, r, s);
        } else {
            assertThrows(IllegalArgumentException.class, () -> new SignatureSecp256k1(v, r, s));
        }
    }

    @Test
    public void gethTestValidateSignatureValues() {
        // inspired by ethereum test:
        //https://github.com/ethereum/go-ethereum/blob/master/crypto/crypto_test.go

        BigInteger minusOne = BigInteger.ONE.negate();
        BigInteger one = BigInteger.ONE;
        BigInteger zero = BigInteger.ZERO;
        BigInteger secp256k1nMinus1 = secp256k1N.subtract(one); // r upper limit is strict
        BigInteger v0 = BigInteger.valueOf(27);
        BigInteger v1 = BigInteger.valueOf(28);

        // upper limit for s is lower that upper limit for r
        assertTrue(secp256k1N.compareTo(secp256k1halfN) > 0);

        // correct v,r,s
        check(true, v0, one, one);
        check(true, v1, one, one);
        // incorrect v, correct r,s,
        check(false, v1.add(one), one, one);
        check(false, v0.negate(), one, one);

        // incorrect v, combinations of incorrect/correct r,s at lower limit
        check(false, v1.add(one), zero, zero);
        check(false, v1.add(one), zero, one);
        check(false, v1.add(one), one, zero);
        check(false, v1.add(one), one, one);

        // correct v for any combination of incorrect r,s
        check(false, v0, zero, zero);
        check(false, v0, zero, one);
        check(false, v0, one, zero);

        check(false, v1, zero, zero);
        check(false, v1, zero, one);
        check(false, v1, one, zero);

        // correct sig with max r,s
        check(true, v0, secp256k1nMinus1, secp256k1halfN);

        // correct v, combinations of incorrect r,s at upper limit
        check(false, v0, secp256k1N, secp256k1halfN); // r too big, s OK
        check(false, v0, secp256k1nMinus1, secp256k1halfN.add(one)); //r OK,  s too big
        check(false, v0, secp256k1N, secp256k1halfN.add(one)); // both too big

        // r,s cannot be negative
        check(false, v0, minusOne, one);
        check(false, v0, one, minusOne);
    }

}
