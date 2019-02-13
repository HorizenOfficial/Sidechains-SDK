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

import static org.junit.Assert.assertEquals;

public class CertifierRightBoxSerializerTest
{
    CertifierRightBox box;

    @Before
    public void setUp() {
        Tuple2<byte[], byte[]> keyPair = Curve25519.createKeyPair("12345".getBytes());
        // Note: current box bytes are also stored in "src/test/resources/certifierrightbox_bytes"
        box = new CertifierRightBox(new PublicKey25519Proposition(keyPair._2()), 1000);
    }

    @Test
    public void CertifierRightBoxSerializer_SerializationTest() {
        BoxSerializer<CertifierRightBox> serializer = box.serializer();
        byte[] bytes = serializer.toBytes(box);
        /*try {
            java.io.FileOutputStream fos = new java.io.FileOutputStream("certifierrightbox_bytes");
            fos.write(bytes);
        } catch (Exception e) {}*/
        CertifierRightBox box2 = serializer.parseBytes(bytes).get();
        assertEquals("Boxes expected to be equal", true, box.equals(box2));


        boolean failureExpected = serializer.parseBytes("broken bytes".getBytes()).isFailure();
        assertEquals("Failure during parsing expected", true, failureExpected);

    }

    @Test
    public void CertifierRightBoxSerializer_RegressionTest() {
        byte[] bytes;
        try {
            ClassLoader classLoader = getClass().getClassLoader();
            File file = new File(classLoader.getResource("certifierrightbox_bytes").getFile());
            bytes = Files.readAllBytes(file.toPath());
        }
        catch (Exception e) {
            assertEquals(e.toString(), true, false);
            return;
        }

        BoxSerializer<CertifierRightBox> serializer = box.serializer();
        Try<CertifierRightBox> t = serializer.parseBytes(bytes);
        assertEquals("Box serialization failed.", true, t.isSuccess());

        CertifierRightBox parsedBox = t.get();
        assertEquals("Box is different to origin.", true, Arrays.equals(box.id(), parsedBox.id()));
        assertEquals("Box is different to origin.", true, Arrays.equals(box.bytes(), parsedBox.bytes()));
    }
}