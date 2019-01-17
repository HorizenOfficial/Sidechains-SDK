package com.horizen.secret;

import com.horizen.proposition.ProofOfKnowledgeProposition;
import com.horizen.proposition.PublicKey25519Proposition;
import org.junit.Test;
import scala.Tuple2;
import scala.util.Try;

import java.util.Random;

import static org.junit.Assert.*;

public class PrivateKey25519SerializerTest {

    @Test
    public void toBytes() {

        byte[] seed = new byte[32];
        new Random().nextBytes(seed);

        Tuple2<PrivateKey25519, PublicKey25519Proposition> keyTuple = PrivateKey25519.generateKeys(seed);
        PrivateKey25519 key = keyTuple._1;

        SecretSerializer<PrivateKey25519> serializer = key.serializer();

        byte[] keyBytes = serializer.toBytes(key);
        Try<PrivateKey25519> t = serializer.parseBytes(keyBytes);

        assertEquals("Keys are not the same.", key, t.get());
    }
}