package com.horizen.box;

import com.horizen.proposition.PublicKey25519Proposition;
import com.horizen.utils.BytesUtils;
import com.horizen.utils.Ed25519;
import com.horizen.utils.Pair;
import org.junit.Before;
import org.junit.Test;
import scala.util.Try;

import java.io.*;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;

public class CertifierRightBoxSerializerTest
{
    CertifierRightBox box;

    @Before
    public void setUp() {
        Pair<byte[], byte[]> keyPair = Ed25519.createKeyPair("12345".getBytes());
        // Note: current box bytes are also stored in "src/test/resources/certifierrightbox_hex"
        box = new CertifierRightBox(new PublicKey25519Proposition(keyPair.getValue()), 1000, 20, 10);

//        try {
//            BufferedWriter out = new BufferedWriter(new FileWriter("src/test/resources/certifierrightbox_hex"));
//            out.write(BytesUtils.toHexString(box.bytes()));
//            out.close();
//        } catch (Throwable e) {
//        }
    }

    @Test
    public void CertifierRightBoxSerializer_SerializationTest() {
        BoxSerializer<CertifierRightBox> serializer = box.serializer();
        byte[] bytes = serializer.toBytes(box);
        CertifierRightBox box2 = serializer.parseBytesTry(bytes).get();
        assertEquals("Boxes expected to be equal", true, box.equals(box2));


        boolean failureExpected = serializer.parseBytesTry("broken bytes".getBytes()).isFailure();
        assertEquals("Failure during parsing expected", true, failureExpected);

    }

    @Test
    public void CertifierRightBoxSerializer_RegressionTest() {
        byte[] bytes;
        try {
            ClassLoader classLoader = getClass().getClassLoader();
            FileReader file = new FileReader(classLoader.getResource("certifierrightbox_hex").getFile());
            bytes = BytesUtils.fromHexString(new BufferedReader(file).readLine());
        }
        catch (Exception e) {
            assertEquals(e.toString(), true, false);
            return;
        }

        BoxSerializer<CertifierRightBox> serializer = box.serializer();
        Try<CertifierRightBox> t = serializer.parseBytesTry(bytes);
        assertEquals("Box serialization failed.", true, t.isSuccess());

        CertifierRightBox parsedBox = t.get();
        assertEquals("Box is different to origin.", true, Arrays.equals(box.id(), parsedBox.id()));
        assertEquals("Box is different to origin.", true, Arrays.equals(box.bytes(), parsedBox.bytes()));
    }
}