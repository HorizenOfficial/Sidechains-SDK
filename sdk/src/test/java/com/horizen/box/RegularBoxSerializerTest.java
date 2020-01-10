package com.horizen.box;

import com.horizen.fixtures.BoxFixtureClass;
import com.horizen.proposition.PublicKey25519Proposition;
import com.horizen.utils.BytesUtils;
import com.horizen.utils.Ed25519;
import com.horizen.utils.Pair;
import org.junit.Before;
import org.junit.Test;
import scala.util.Try;

import java.io.*;
import java.util.Arrays;

import static org.junit.Assert.*;

public class RegularBoxSerializerTest extends BoxFixtureClass
{
    RegularBox box;

    @Before
    public void setUp() {
        Pair<byte[], byte[]> keyPair = Ed25519.createKeyPair("12345".getBytes());
        // Note: current box bytes are also stored in "src/test/resources/regularbox_hex"
        box = getRegularBox(new PublicKey25519Proposition(keyPair.getValue()), 1000, 10);

//     Uncomment and run if you want to update regression data.
//        try {
//            BufferedWriter out = new BufferedWriter(new FileWriter("src/test/resources/regularbox_hex"));
//            out.write(BytesUtils.toHexString(box.bytes()));
//            out.close();
//        } catch (Throwable e) {
//        }
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
            FileReader file = new FileReader(classLoader.getResource("regularbox_hex").getFile());
            bytes = BytesUtils.fromHexString(new BufferedReader(file).readLine());
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