package com.horizen.account.secret;

import com.horizen.account.utils.Secp256k1;
import com.horizen.secret.SecretSerializer;
import org.junit.Before;
import org.junit.Test;
import org.web3j.crypto.ECKeyPair;
import org.web3j.crypto.Keys;
import scala.util.Try;

import java.security.InvalidAlgorithmParameterException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class PrivateKeySecp256k1SerializerTest {
    PrivateKeySecp256k1 privateKeySecp256k1;

    @Before
    public void BeforeEachTest() throws InvalidAlgorithmParameterException, NoSuchAlgorithmException, NoSuchProviderException {
        // Create a key pair and create secret
        ECKeyPair pair = Keys.createEcKeyPair();
        byte[] privateKey = Arrays.copyOf(pair.getPrivateKey().toByteArray(), Secp256k1.PRIVATE_KEY_SIZE);
        privateKeySecp256k1 = new PrivateKeySecp256k1(privateKey);
    }

    @Test
    public void signatureSecp256k1SerializeTest() {
        // Get secret serializer and serialize
        SecretSerializer serializer = privateKeySecp256k1.serializer();
        byte[] bytes = serializer.toBytes(privateKeySecp256k1);

        // Test 1: Correct bytes deserialization
        Try<PrivateKeySecp256k1> t = serializer.parseBytesTry(bytes);
        assertTrue("Secret serialization failed.", t.isSuccess());
        assertEquals("Deserialized secret expected to be equal", privateKeySecp256k1.toString(), t.get().toString());

        // Test 2: try to parse broken bytes
        boolean failureExpected = serializer.parseBytesTry("broken bytes".getBytes()).isFailure();
        assertTrue("Failure during parsing expected", failureExpected);
    }
}
