package io.horizen.account.proof;

import io.horizen.account.proposition.AddressProposition;
import io.horizen.account.secret.PrivateKeySecp256k1;
import io.horizen.account.secret.PrivateKeySecp256k1Creator;
import org.junit.Before;
import org.junit.Test;
import org.web3j.utils.Numeric;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;

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
        AddressProposition newAddressProposition = PrivateKeySecp256k1Creator.getInstance().generateSecret("sigtest_other".getBytes(StandardCharsets.UTF_8)).publicImage();
        assertFalse(signatureSecp256k1.isValid(newAddressProposition, message));

        // Test 7: Returns true for valid signature
        assertTrue(signatureSecp256k1.isValid(addressProposition, message));
    }
}
