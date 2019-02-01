package com.horizen.secret;

import com.horizen.proposition.ProofOfKnowledgeProposition;
import com.horizen.proposition.PublicKey25519Proposition;
import org.junit.Test;
import scala.util.Try;

import java.util.Random;

import static org.junit.Assert.*;

public class PrivateKey25519SerializerTest {

    @Test
    public void toBytes() {

        byte[] seed = new byte[32];
        new Random().nextBytes(seed);

        PrivateKey25519 key = PrivateKey25519Companion.getCompanion().generateSecret(seed);

        SecretSerializer<PrivateKey25519> serializer = key.serializer();

        byte[] keyBytes = serializer.toBytes(key);
        Try<PrivateKey25519> t = serializer.parseBytes(keyBytes);

        assertEquals("Keys are not the same.", key, t.get());
    }
}