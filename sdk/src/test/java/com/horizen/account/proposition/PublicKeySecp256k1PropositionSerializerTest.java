package com.horizen.account.proposition;

import com.horizen.proposition.PropositionSerializer;
import org.bouncycastle.util.Strings;
import org.junit.Before;
import org.junit.Test;
import org.web3j.crypto.ECKeyPair;
import org.web3j.crypto.Keys;
import scala.util.Try;

import java.security.InvalidAlgorithmParameterException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class PublicKeySecp256k1PropositionSerializerTest {
    PublicKeySecp256k1Proposition publicKeySecp256k1Proposition;

    @Before
    public void BeforeEachTest() throws InvalidAlgorithmParameterException, NoSuchAlgorithmException, NoSuchProviderException {
        // Create a key pair and create proposition
        ECKeyPair pair = Keys.createEcKeyPair();
        byte[] address = Arrays.copyOf(Strings.toByteArray("0x"+Keys.getAddress(pair)), Keys.ADDRESS_LENGTH_IN_HEX+2);
        publicKeySecp256k1Proposition = new PublicKeySecp256k1Proposition(address);
    }

    @Test
    public void signatureSecp256k1SerializeTest() {
        // Get proposition serializer and serialize
        PropositionSerializer serializer = publicKeySecp256k1Proposition.serializer();
        byte[] bytes = serializer.toBytes(publicKeySecp256k1Proposition);

        // Test 1: Correct bytes deserialization
        Try<PublicKeySecp256k1Proposition> t = serializer.parseBytesTry(bytes);
        assertTrue("Proposition serialization failed.", t.isSuccess());
        assertEquals("Deserialized proposition expected to be equal", publicKeySecp256k1Proposition.toString(), t.get().toString());

        // Test 2: try to parse broken bytes
        boolean failureExpected = serializer.parseBytesTry("broken bytes".getBytes()).isFailure();
        assertTrue("Failure during parsing expected", failureExpected);
    }
}
