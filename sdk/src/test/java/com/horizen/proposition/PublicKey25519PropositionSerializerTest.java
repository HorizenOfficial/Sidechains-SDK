package com.horizen.proposition;

import com.horizen.utils.BytesUtils;
import com.horizen.utils.Ed25519;
import com.horizen.utils.Pair;
import org.junit.Before;
import org.junit.Test;
import scala.util.Try;

import java.io.*;
import java.util.Arrays;

import static org.junit.Assert.*;

public class PublicKey25519PropositionSerializerTest {

    PublicKey25519Proposition proposition;

    @Before
    public void beforeEachTest() {
        Pair<byte[], byte[]> keyPair = Ed25519.createKeyPair("12345".getBytes());
        // Note: current proposition bytes are also stored in "src/test/resources/publickey25519proposition_hex"
        proposition = new PublicKey25519Proposition(keyPair.getValue());

//     Uncomment and run if you want to update regression data.
//        try {
//            BufferedWriter out = new BufferedWriter(new FileWriter("src/test/resources/publickey25519proposition_hex"));
//            out.write(BytesUtils.toHexString(proposition.bytes()));
//            out.close();
//        } catch (Throwable e) {
//        }
    }

    @Test
    public void PublicKey25519PropositionSerializerTest_SerializationTest() {
        PropositionSerializer serializer = proposition.serializer();
        byte[] bytes = serializer.toBytes(proposition);

        Try<PublicKey25519Proposition> t = serializer.parseBytesTry(bytes);
        assertEquals("Propositions expected to be equal", proposition, ((Try) t).get());

        boolean failureExpected = serializer.parseBytesTry("broken bytes".getBytes()).isFailure();
        assertEquals("Failure during parsing expected", true, failureExpected);

    }

    @Test
    public void PublicKey25519PropositionSerializerTest_RegressionTest() {
        byte[] bytes;
        try {
            ClassLoader classLoader = getClass().getClassLoader();
            FileReader file = new FileReader(classLoader.getResource("publickey25519proposition_hex").getFile());
            bytes = BytesUtils.fromHexString(new BufferedReader(file).readLine());
        }
        catch (Exception e) {
            assertEquals(e.toString(), true, false);
            return;
        }

        PropositionSerializer serializer = proposition.serializer();
        Try<PublicKey25519Proposition> t = serializer.parseBytesTry(bytes);
        assertEquals("Proposition serialization failed.", true, t.isSuccess());

        PublicKey25519Proposition parsedProposition = t.get();
        assertEquals("Proposition is different to origin.", true, Arrays.equals(proposition.pubKeyBytes(), parsedProposition.pubKeyBytes()));
    }
}