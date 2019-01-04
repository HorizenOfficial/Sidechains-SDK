package com.horizen.proposition;

import org.junit.Before;
import org.junit.Test;
import scala.Tuple2;
import scorex.crypto.signatures.Curve25519;

import static org.junit.Assert.*;

public class PublicKey25519PropositionSerializerTest {
    PublicKey25519Proposition proposition;

    @Before
    public void beforeEachTest() {
        Tuple2<byte[], byte[]> keyPair = Curve25519.createKeyPair("12345".getBytes());
        proposition = new PublicKey25519Proposition(keyPair._2());
    }

    @Test
    public void PublicKey25519PropositionSerializerTest_SerializationTest() {
        PublicKey25519PropositionSerializer serializer = new PublicKey25519PropositionSerializer();
        byte[] bytes = serializer.toBytes(proposition);

        PublicKey25519Proposition proposition2 = serializer.parseBytes(bytes).get();
        assertEquals("Propositions expected to be equal", true, proposition.equals(proposition2));

        boolean failureExpected = serializer.parseBytes("broken bytes".getBytes()).isFailure();
        assertEquals("Failure during parsing expected", true, failureExpected);

    }

}