package com.horizen.box;

import com.horizen.fixtures.BoxFixtureClass;
import com.horizen.proposition.PublicKey25519Proposition;
import com.horizen.utils.BytesUtils;
import com.horizen.utils.Ed25519;
import com.horizen.utils.Pair;
import com.horizen.vrf.VrfGeneratedDataProvider;
import org.junit.Before;
import org.junit.Test;
import scala.util.Try;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ForgerBoxSerializationTest extends BoxFixtureClass
{
    ForgerBox box;

    @Before
    public void setUp() {
        int vrfGenerationSeed = 901;
        String vrfGenerationPrefix = "ForgerBoxSerializationTest";

        //uncomment if you want update vrf related data
        if (false) {
            VrfGeneratedDataProvider.updateVrfPublicKey(vrfGenerationPrefix, vrfGenerationSeed);
        }

        Pair<byte[], byte[]> keyPair = Ed25519.createKeyPair("12345".getBytes());
        // Note: current box bytes are also stored in "src/test/resources/forgerbox_hex"
        box = getForgerBox(
                new PublicKey25519Proposition(keyPair.getValue()),
                1000,
                10,
                new PublicKey25519Proposition(keyPair.getValue()),
                VrfGeneratedDataProvider.getVrfPublicKey(vrfGenerationPrefix, vrfGenerationSeed));

     //Set to true and run if you want to update regression data.
        if (false) {
            try {
                BufferedWriter out = new BufferedWriter(new FileWriter("src/test/resources/forgerbox_hex"));
                out.write(BytesUtils.toHexString(box.bytes()));
                out.close();
            }
            catch (Throwable e) {
            }
        }
    }

    @Test
    public void serializationTest() {
        BoxSerializer<ForgerBox> serializer = box.serializer();
        byte[] bytes = serializer.toBytes(box);

        ForgerBox box2 = serializer.parseBytesTry(bytes).get();
        assertEquals("Boxes expected to be equal", box, box2);


        boolean failureExpected = serializer.parseBytesTry("broken bytes".getBytes()).isFailure();
        assertTrue("Failure during parsing expected", failureExpected);
    }

    @Test
    public void regressionTest() {
        byte[] bytes;
        try {
            ClassLoader classLoader = getClass().getClassLoader();
            FileReader file = new FileReader(classLoader.getResource("forgerbox_hex").getFile());
            bytes = BytesUtils.fromHexString(new BufferedReader(file).readLine());
        }
        catch (Exception e) {
            fail(e.toString());
            return;
        }

        BoxSerializer<ForgerBox> serializer = box.serializer();
        Try<ForgerBox> t = serializer.parseBytesTry(bytes);
        assertTrue("Box serialization failed.", t.isSuccess());

        ForgerBox parsedBox = t.get();
        assertEquals("Box id is different to origin.", BytesUtils.toHexString(box.id()), BytesUtils.toHexString(parsedBox.id()));
        assertEquals("Box is different to origin.", BytesUtils.toHexString(box.bytes()), BytesUtils.toHexString(parsedBox.bytes()));
    }
}