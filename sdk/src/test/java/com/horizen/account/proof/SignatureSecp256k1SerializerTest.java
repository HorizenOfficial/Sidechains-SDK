package com.horizen.account.proof;

import com.horizen.account.secret.PrivateKeySecp256k1;
import com.horizen.account.secret.PrivateKeySecp256k1Creator;
import com.horizen.proof.ProofSerializer;
import org.junit.Before;
import org.junit.Test;
import scala.util.Try;

import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SignatureSecp256k1SerializerTest {
    SignatureSecp256k1 signatureSecp256k1;

    @Before
    public void BeforeEachTest() {
        // Create the raw Transaction
        String payload = "This is string to sign";
        var message = payload.getBytes(StandardCharsets.UTF_8);

        // Create a key and generate the signature
        PrivateKeySecp256k1 privateKey = PrivateKeySecp256k1Creator.getInstance().generateSecret("seed".getBytes());
        signatureSecp256k1 = privateKey.sign(message);
    }

    @Test
    public void signatureSecp256k1SerializeTest() {
        // Get proof serializer and serialize
        ProofSerializer serializer = signatureSecp256k1.serializer();
        byte[] bytes = serializer.toBytes(signatureSecp256k1);

        // Test 1: Correct bytes deserialization
        Try<SignatureSecp256k1> t = serializer.parseBytesTry(bytes);
        assertTrue("Proof serialization failed with " + t.toString() + ".", t.isSuccess());
        assertEquals("Deserialized proof expected to be equal", signatureSecp256k1.toString(), t.get().toString());

        // Test 2: try to parse broken bytes
        boolean failureExpected = serializer.parseBytesTry("broken bytes".getBytes()).isFailure();
        assertTrue("Failure during parsing expected", failureExpected);
    }
}
