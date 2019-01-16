package com.horizen.secret;

import com.horizen.proposition.ProofOfKnowledgeProposition;
import org.junit.Test;
import scala.Tuple2;
import scala.util.Try;

import java.util.Random;

import static org.junit.Assert.*;

public class PrivateKey25519SerializerTest {

/*
    @Test
    public void toBytes() {

        byte[] seed = new byte[32];
        new Random().nextBytes(seed);

        Tuple2<Secret, ProofOfKnowledgeProposition<Secret>> keyTuple = PrivateKey25519.generateKeys(seed);
        PrivateKey25519 key = (PrivateKey25519) keyTuple._1;

        PrivateKey25519Serializer serializer = new PrivateKey25519Serializer();

        byte[] keyBytes = serializer.toBytes(key);
        Try<PrivateKey25519> t = serializer.parseBytes(keyBytes);

        assertEquals("Keys are not the same.", key, t.get());
    }
*/
}