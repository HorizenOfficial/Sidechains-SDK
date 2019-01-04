package com.horizen.box;

import com.horizen.proposition.PublicKey25519Proposition;
import org.junit.Before;
import org.junit.Test;
import scala.Tuple2;
import scorex.crypto.signatures.Curve25519;

import static org.junit.Assert.*;

public class RegularBoxSerializerTest
{
    RegularBox box;

    @Before
    public void setUp() {
        Tuple2<byte[], byte[]> keyPair = Curve25519.createKeyPair("12345".getBytes());
        box = new RegularBox(new PublicKey25519Proposition(keyPair._2()), 1000, 10);
    }

    @Test
    public void RegularBoxSerializerTest_SerializationTest() {
        RegularBoxSerializer serializer = new RegularBoxSerializer();
        byte[] bytes = serializer.toBytes(box);

        RegularBox box2 = serializer.parseBytes(bytes).get();
        assertEquals("Boxes expected to be equal", true, box.equals(box2));


        boolean failureExpected = serializer.parseBytes("broken bytes".getBytes()).isFailure();
        assertEquals("Failure during parsing expected", true, failureExpected);

    }
}