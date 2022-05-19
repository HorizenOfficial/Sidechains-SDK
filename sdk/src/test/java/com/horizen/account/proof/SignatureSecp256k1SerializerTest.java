package com.horizen.account.proof;

import com.horizen.proof.ProofSerializer;
import org.junit.Before;
import org.junit.Test;
import org.web3j.crypto.ECKeyPair;
import org.web3j.crypto.Keys;
import org.web3j.crypto.RawTransaction;
import org.web3j.crypto.Sign;
import scala.util.Try;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.InvalidAlgorithmParameterException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SignatureSecp256k1SerializerTest {
    SignatureSecp256k1 signatureSecp256k1;

    @Before
    public void BeforeEachTest() throws InvalidAlgorithmParameterException, NoSuchAlgorithmException, NoSuchProviderException {
        // Create the raw Transaction
        String payload = "This is string to sign";
        var message = payload.getBytes(StandardCharsets.UTF_8);
        var someValue = BigInteger.ONE;
        var rawTX = RawTransaction.createTransaction(someValue,
                someValue, someValue, "0x", someValue, "");

        // Create a key pair and create the signature
        ECKeyPair pair = Keys.createEcKeyPair();
        var msgSignature = Sign.signMessage(message, pair, true);
        signatureSecp256k1 = new SignatureSecp256k1(msgSignature);
    }

    @Test
    public void signatureSecp256k1SerializeTest() {
        // Get proof serializer and serialize
        ProofSerializer serializer = signatureSecp256k1.serializer();
        byte[] bytes = serializer.toBytes(signatureSecp256k1);

        // Test 1: Correct bytes deserialization
        Try<SignatureSecp256k1> t = serializer.parseBytesTry(bytes);
        assertTrue("Proof serialization failed.", t.isSuccess());
        assertEquals("Deserialized proof expected to be equal", signatureSecp256k1.toString(), t.get().toString());

        // Test 2: try to parse broken bytes
        boolean failureExpected = serializer.parseBytesTry("broken bytes".getBytes()).isFailure();
        assertTrue("Failure during parsing expected", failureExpected);
    }
}
