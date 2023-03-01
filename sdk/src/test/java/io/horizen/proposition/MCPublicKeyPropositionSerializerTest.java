package io.horizen.proposition;

import io.horizen.utils.BytesUtils;
import org.junit.Before;
import org.junit.Test;
import scala.util.Try;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;

import static org.junit.Assert.*;

public class MCPublicKeyPropositionSerializerTest {

    byte[] publicKeyHashBytes = BytesUtils.fromHexString("b76f72a175a3ed8f30b047032c4a0cad394bf5a9");
    byte[] brokenKeyBytes = new byte[MCPublicKeyHashProposition.KEY_LENGTH - 5];

    MCPublicKeyHashProposition proposition;

    @Before
    public void beforeEachTest() {


        proposition = new MCPublicKeyHashProposition(publicKeyHashBytes);

        //Save box to binary file for regression tests.
        /*
        try {
            BufferedWriter out = new BufferedWriter(new FileWriter("src/test/resources/mcpublickeyhashproposition_hex"));
            out.write(BytesUtils.toHexString(proposition.serializer().toBytes(proposition)));
            out.close();
        } catch (Throwable e) {
        }
        */
    }

    @Test
    public void PublicKey25519PropositionSerializerTest_SerializationTest() {
        PropositionSerializer serializer = proposition.serializer();
        byte[] bytes = serializer.toBytes(proposition);

        Try<MCPublicKeyHashProposition> t = serializer.parseBytesTry(bytes);
        assertTrue("Parse operation mus be successful.", t.isSuccess());
        assertEquals("Propositions expected to be equal", proposition, t.get());

        boolean failureExpected = serializer.parseBytesTry(brokenKeyBytes).isFailure();
        assertEquals("Failure during parsing expected", true, failureExpected);

    }

    @Test
    public void PublicKey25519PropositionSerializerTest_RegressionTest() {
        byte[] bytes;
        try {
            ClassLoader classLoader = getClass().getClassLoader();
            FileReader file = new FileReader(classLoader.getResource("mcpublickeyhashproposition_hex").getFile());
            bytes = BytesUtils.fromHexString(new BufferedReader(file).readLine());
        }
        catch (Exception e) {
            assertEquals(e.toString(), true, false);
            return;
        }

        PropositionSerializer serializer = proposition.serializer();
        Try<MCPublicKeyHashProposition> t = serializer.parseBytesTry(bytes);
        assertEquals("Proposition serialization failed.", true, t.isSuccess());
        assertArrayEquals("Proposition is different to origin.", proposition.bytes(), t.get().bytes());
    }
}