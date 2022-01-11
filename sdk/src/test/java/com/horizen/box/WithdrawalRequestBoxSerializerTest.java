package com.horizen.box;

import com.horizen.fixtures.BoxFixtureClass;
import com.horizen.proposition.MCPublicKeyHashProposition;
import com.horizen.utils.BytesUtils;
import org.junit.Before;
import org.junit.Test;
import scala.util.Try;

import java.io.*;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class WithdrawalRequestBoxSerializerTest extends BoxFixtureClass
{
    WithdrawalRequestBox box;

    @Before
    public void setUp() {
        // Note: current box bytes are also stored in "src/test/resources/withdrawalrequestbox_bytes"
        box = getWithdrawalRequestBox(new MCPublicKeyHashProposition(new byte[MCPublicKeyHashProposition.KEY_LENGTH]), 1000, 10);

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
    public void serializationTest() {
        BoxSerializer<WithdrawalRequestBox> serializer = box.serializer();
        byte[] bytes = serializer.toBytes(box);

        WithdrawalRequestBox box2 = serializer.parseBytes(bytes);
        assertEquals("Boxes expected to be equal", box, box2);


        boolean failureExpected = serializer.parseBytesTry("broken bytes".getBytes()).isFailure();
        assertTrue("Failure during parsing expected", failureExpected);

    }

    @Test
    public void regressionTest() {
        byte[] bytes;
        try {
            ClassLoader classLoader = getClass().getClassLoader();
            FileReader file = new FileReader(classLoader.getResource("withdrawalrequestbox_hex").getFile());
            bytes = BytesUtils.fromHexString(new BufferedReader(file).readLine());
        }
        catch (Exception e) {
            fail(e.toString());
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