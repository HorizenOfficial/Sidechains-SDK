package com.horizen.secret;


import org.junit.Before;
import org.junit.Test;
import scala.util.Try;

import java.io.File;
import java.nio.file.Files;

import static org.junit.Assert.*;

public class PrivateKey25519SerializerTest {

    PrivateKey25519 key;

    @Before
    public void beforeEachTest() {
        // Note: current secret bytes are also stored in "src/test/resources/privatekey25519_bytes"
        key = PrivateKey25519Creator.getInstance().generateSecret("12345".getBytes());
    }

    @Test
    public void PrivateKey25519SerializerTest_SerializationTest() {
        SecretSerializer<PrivateKey25519> serializer = key.serializer();
        byte[] keyBytes = serializer.toBytes(key);
        Try<PrivateKey25519> t = serializer.parseBytesTry(keyBytes);

        assertEquals("Keys are not the same.", key, t.get());
    }

    @Test
    public void PrivateKey25519SerializerTest_RegressionTest() {
        byte[] bytes;
        try {
            ClassLoader classLoader = getClass().getClassLoader();
            File file = new File(classLoader.getResource("privatekey25519_bytes").getFile());
            bytes = Files.readAllBytes(file.toPath());
        }
        catch (Exception e) {
            assertEquals(e.toString(), true, false);
            return;
        }

        SecretSerializer<PrivateKey25519> serializer = key.serializer();
        Try<PrivateKey25519> t = serializer.parseBytesTry(bytes);
        assertEquals("Secret serialization failed.", true, t.isSuccess());

        PrivateKey25519 parsedSecret = t.get();
        assertEquals("Secret is different to origin.", true, key.equals(parsedSecret));
    }
}