package com.horizen.proposition;

import com.horizen.proposition.PublicKey25519Proposition;
import com.horizen.proposition.PublicKey25519PropositionSerializer;
import org.junit.Before;
import org.junit.Test;
import scala.Tuple2;
import scala.util.Try;
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
        PropositionSerializer serializer = proposition.serializer();
        byte[] bytes = serializer.toBytes(proposition);

        Try<PublicKey25519Proposition> t = serializer.parseBytes(bytes);
        assertEquals("Propositions expected to be equal", proposition, ((Try) t).get());

        boolean failureExpected = serializer.parseBytes("broken bytes".getBytes()).isFailure();
        assertEquals("Failure during parsing expected", true, failureExpected);

    }
}