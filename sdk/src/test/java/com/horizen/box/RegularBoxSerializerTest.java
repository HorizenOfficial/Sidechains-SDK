package com.horizen.box;

import com.horizen.proposition.PublicKey25519Proposition;
import org.junit.Before;
import org.junit.Test;
import scala.Tuple2;
import scala.util.Try;
import scorex.crypto.signatures.Curve25519;

import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;

import static org.junit.Assert.*;

public class RegularBoxSerializerTest
{
    RegularBox box;

    @Before
    public void setUp() {
        Tuple2<byte[], byte[]> keyPair = Curve25519.createKeyPair("12345".getBytes());
        // Note: current box bytes are also stored in "src/test/resources/regularbox_bytes"
        box = new RegularBox(new PublicKey25519Proposition(keyPair._2()), 1000, 10);
    }

    @Test
    public void RegularBoxSerializerTest_SerializationTest() {
        BoxSerializer<RegularBox> serializer = box.serializer();
        byte[] bytes = serializer.toBytes(box);

        RegularBox box2 = serializer.parseBytesTry(bytes).get();
        assertEquals("Boxes expected to be equal", true, box.equals(box2));


        boolean failureExpected = serializer.parseBytesTry("broken bytes".getBytes()).isFailure();
        assertEquals("Failure during parsing expected", true, failureExpected);

    }

    @Test
    public void RegularBoxSerializerTest_RegressionTest() {
        byte[] bytes;
        try {
            ClassLoader classLoader = getClass().getClassLoader();
            File file = new File(classLoader.getResource("regularbox_bytes").getFile());
            bytes = Files.readAllBytes(file.toPath());
        }
        catch (Exception e) {
            assertEquals(e.toString(), true, false);
            return;
        }

        BoxSerializer<RegularBox> serializer = box.serializer();
        Try<RegularBox> t = serializer.parseBytesTry(bytes);
        assertEquals("Box serialization failed.", true, t.isSuccess());

        RegularBox parsedBox = t.get();
        assertEquals("Box is different to origin.", true, Arrays.equals(box.id(), parsedBox.id()));
        assertEquals("Box is different to origin.", true, Arrays.equals(box.bytes(), parsedBox.bytes()));
    }
}