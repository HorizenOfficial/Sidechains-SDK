package com.horizen.box;

import com.horizen.proposition.MCPublicKeyHashProposition;
import com.horizen.utils.BytesUtils;
import org.junit.Before;
import org.junit.Test;
import scala.Tuple2;
import scala.util.Try;
import scorex.crypto.signatures.Curve25519;

import java.io.*;
import java.nio.file.Files;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class WithdrawalRequestBoxSerializerTest
{
    WithdrawalRequestBox box;

    @Before
    public void setUp() {
        // Note: current box bytes are also stored in "src/test/resources/withdrawalrequestbox_bytes"
        box = new WithdrawalRequestBox(new MCPublicKeyHashProposition(new byte[MCPublicKeyHashProposition.KEY_LENGTH]), 1000, 10);

        //Save box to binary file for regression tests.
        /*
        try {
            BufferedWriter out = new BufferedWriter(new FileWriter("src/test/resources/withdrawalrequestbox_hex"));
            out.write(BytesUtils.toHexString(box.serializer().toBytes(box)));
            out.close();
        } catch (Throwable e) {
        }
        */
    }

    @Test
    public void WithdrawalRequestBoxSerializerTest_SerializationTest() {
        BoxSerializer<WithdrawalRequestBox> serializer = box.serializer();
        byte[] bytes = serializer.toBytes(box);

        WithdrawalRequestBox box2 = serializer.parseBytes(bytes);
        assertTrue("Boxes expected to be equal", box.equals(box2));


        boolean failureExpected = serializer.parseBytesTry("broken bytes".getBytes()).isFailure();
        assertTrue("Failure during parsing expected", failureExpected);

    }

    @Test
    public void WithdrawalRequestBoxSerializerTest_RegressionTest() {
        byte[] bytes;
        try {
            ClassLoader classLoader = getClass().getClassLoader();
            FileReader file = new FileReader(classLoader.getResource("withdrawalrequestbox_hex").getFile());
            bytes = BytesUtils.fromHexString(new BufferedReader(file).readLine());
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