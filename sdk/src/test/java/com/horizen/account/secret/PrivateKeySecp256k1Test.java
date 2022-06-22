package com.horizen.account.secret;

import com.horizen.account.proof.SignatureSecp256k1;
import com.horizen.account.proposition.AddressProposition;
import com.horizen.account.utils.Secp256k1;
import org.junit.Before;
import org.junit.Test;
import org.web3j.crypto.ECKeyPair;
import org.web3j.crypto.Hash;
import org.web3j.crypto.Sign;
import org.web3j.utils.Numeric;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static com.horizen.account.secret.SecretsIdsEnum.PrivateKeySecp256k1SecretId;
import static org.junit.Assert.*;

public class PrivateKeySecp256k1Test {
    PrivateKeySecp256k1 privateKeySecp256k1;
    byte[] privateKey;
    ECKeyPair pair;
    byte[] hashedKey;
    AddressProposition addressProposition;

    @Before
    public void BeforeEachTest() {
        // Set private key, create key pair, create private key secp256k1, create address proposition
        privateKey = Arrays.copyOf(new BigInteger("4164020894437499837987386266872312011773540175446618547506457344").toByteArray(), Secp256k1.PRIVATE_KEY_SIZE);
        pair = ECKeyPair.create(privateKey);
        privateKeySecp256k1 = new PrivateKeySecp256k1(privateKey);
        hashedKey = Hash.sha3(Numeric.toBytesPadded(pair.getPublicKey(), 64));
        addressProposition = new AddressProposition(Arrays.copyOf(Arrays.copyOfRange(hashedKey, hashedKey.length - 20, hashedKey.length), 20));
    }

    @Test
    public void privateKeySecp256k1Test() {
        // Test 1: Returns correct address proposition from private key
        assertArrayEquals(privateKeySecp256k1.publicImage().address(), Numeric.hexStringToByteArray("d51c15351339f694fe234e12a036746721afb506"));

        // Test 2: Returns correct signature for message
        String payload = "This is string to sign";
        var message = payload.getBytes(StandardCharsets.UTF_8);
        assertEquals(privateKeySecp256k1.sign(message).toString(), new SignatureSecp256k1(Sign.signMessage(message, pair, true)).toString());

        // Test 3: Must return true as it owns here created address proposition
        assertTrue(privateKeySecp256k1.owns(addressProposition));

        // Test 4: Returns hash code correctly
        assertEquals(Arrays.hashCode(privateKey), privateKeySecp256k1.hashCode());

        // Test 5: Returns true as the object is the same
        assertTrue(privateKeySecp256k1.equals(privateKeySecp256k1));

        // Test 6: Returns false as the object is null
        assertFalse(privateKeySecp256k1.equals(null));

        // Test 7: Returns false as the object is not an instance of PrivateKeySecp256k1
        assertFalse(privateKeySecp256k1.equals(addressProposition));

        // Test 8: Returns false as the object does not contain the same private key
        PrivateKeySecp256k1 anotherPrivateKeySecp256k1 = new PrivateKeySecp256k1(Arrays.copyOf(new BigInteger("1234567890437499837987386266872312011773540175446618547506457344").toByteArray(), Secp256k1.PRIVATE_KEY_SIZE));
        assertFalse(privateKeySecp256k1.equals(anotherPrivateKeySecp256k1));

        // Test 9: Returns true as the object contains the same private key
        anotherPrivateKeySecp256k1 = new PrivateKeySecp256k1(Arrays.copyOf(new BigInteger("4164020894437499837987386266872312011773540175446618547506457344").toByteArray(), Secp256k1.PRIVATE_KEY_SIZE));
        assertTrue(privateKeySecp256k1.equals(anotherPrivateKeySecp256k1));

        // Test 10: Returns PrivateKeySecp256k1SecretId correctly
        assertEquals(PrivateKeySecp256k1SecretId.id(), privateKeySecp256k1.secretTypeId());

        // Test 11: Illegal Argument Exception while creating new PrivateKeySecp256k1 expected
        assertThrows(IllegalArgumentException.class, () -> new PrivateKeySecp256k1(new BigInteger("4164020894437499837987386266872312011773540175446618547506457344").toByteArray()));

        // Test 12: Nullpointer Exception while creating new PrivateKeySecp256k1 expected
        assertThrows(NullPointerException.class, () -> new PrivateKeySecp256k1(null));
    }
}
