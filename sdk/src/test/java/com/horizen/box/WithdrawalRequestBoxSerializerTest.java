package com.horizen.box;

import com.horizen.proposition.MCPublicKeyHash;
import com.horizen.proposition.PublicKey25519Proposition;
import org.junit.Before;
import org.junit.Test;
import scala.Tuple2;
import scala.util.Try;
import scorex.crypto.signatures.Curve25519;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class WithdrawalRequestBoxSerializerTest
{
    WithdrawalRequestBox box;

    @Before
    public void setUp() {
        Tuple2<byte[], byte[]> keyPair = Curve25519.createKeyPair("12345".getBytes());
        // Note: current box bytes are also stored in "src/test/resources/WithdrawalRequestBox_bytes"
        box = new WithdrawalRequestBox(new MCPublicKeyHash(new byte[MCPublicKeyHash.KEY_LENGTH]), 1000, 10);

        //Save box to binary file for regression tests.
        /*
        try {
            FileOutputStream out = new FileOutputStream("src/test/resources/withdrawalrequestbox_bytes");
            out.write(box.serializer().toBytes(box));
            out.close();
        } catch (Throwable e) {
        }
        */
    }

    @Test
    public void WithdrawalRequestBoxSerializerTest_SerializationTest() {
        BoxSerializer<WithdrawalRequestBox> serializer = box.serializer();
        byte[] bytes = serializer.toBytes(box);

        WithdrawalRequestBox box2 = serializer.parseBytesTry(bytes).get();
        assertTrue("Boxes expected to be equal", box.equals(box2));


        boolean failureExpected = serializer.parseBytesTry("broken bytes".getBytes()).isFailure();
        assertTrue("Failure during parsing expected", failureExpected);

    }

    @Test
    public void WithdrawalRequestBoxSerializerTest_RegressionTest() {
        byte[] bytes;
        try {
            ClassLoader classLoader = getClass().getClassLoader();
            File file = new File(classLoader.getResource("withdrawalrequestbox_bytes").getFile());
            bytes = Files.readAllBytes(file.toPath());
        }
        catch (Exception e) {
            assertTrue(e.toString(), false);
            return;
        }

        BoxSerializer<WithdrawalRequestBox> serializer = box.serializer();
        Try<WithdrawalRequestBox> t = serializer.parseBytesTry(bytes);
        assertTrue("Box serialization failed.", t.isSuccess());

        WithdrawalRequestBox parsedBox = t.get();
        assertTrue("Box is different to origin.", Arrays.equals(box.id(), parsedBox.id()));
        assertTrue("Box is different to origin.", Arrays.equals(box.bytes(), parsedBox.bytes()));
    }
}