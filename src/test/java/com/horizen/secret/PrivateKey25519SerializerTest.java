package com.horizen.secret;

import com.horizen.proposition.ProofOfKnowledgeProposition;
import com.horizen.proposition.PublicKey25519Proposition;
import org.junit.Before;
import org.junit.Test;
import scala.util.Try;

import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Random;

import static org.junit.Assert.*;

public class PrivateKey25519SerializerTest {

    PrivateKey25519 key;

    @Before
    public void beforeEachTest() {
        // Note: current secret bytes are also stored in "src/test/resources/privatekey25519_bytes"
        key = PrivateKey25519Companion.getCompanion().generateSecret("12345".getBytes());
    }

    @Test
    public void PrivateKey25519SerializerTest_SerializationTest() {
        SecretSerializer<PrivateKey25519> serializer = key.serializer();
        byte[] keyBytes = serializer.toBytes(key);
        Try<PrivateKey25519> t = serializer.parseBytes(keyBytes);

        assertEquals("Keys are not the same.", key, t.get());
    }

    @Test
    public void PrivateKey25519SerializerTest_RegressionTest() {
        byte[] bytes;
        try {
            bytes = Files.readAllBytes(new File("src\\test\\resources\\privatekey25519_bytes").toPath());
        }
        catch (Exception e) {
            assertEquals(e.toString(), true, false);
            return;
        }

        SecretSerializer<PrivateKey25519> serializer = key.serializer();
        Try<PrivateKey25519> t = serializer.parseBytes(bytes);
        assertEquals("Secret serialization failed.", true, t.isSuccess());

        PrivateKey25519 parsedSecret = t.get();
        assertEquals("Secret is different to origin.", true, key.equals(parsedSecret));
    }
}