package com.horizen.account.proposition;

import com.horizen.account.utils.Account;
import com.horizen.proposition.PropositionSerializer;
import com.horizen.utils.BytesUtils;
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

public class AddressPropositionSerializerTest {
    AddressProposition addressProposition;

    @Before
    public void BeforeEachTest() throws InvalidAlgorithmParameterException, NoSuchAlgorithmException, NoSuchProviderException {
        // Create a key pair and create proposition
        ECKeyPair pair = Keys.createEcKeyPair();
        byte[] address = Arrays.copyOf(BytesUtils.fromHexString(Keys.getAddress(pair)), Account.ADDRESS_SIZE);
        addressProposition = new AddressProposition(address);
    }

    @Test
    public void addressPropositionSerializeTest() {
        // Get proposition serializer and serialize
        PropositionSerializer serializer = addressProposition.serializer();
        byte[] bytes = serializer.toBytes(addressProposition);

        // Test 1: Correct bytes deserialization
        Try<AddressProposition> t = serializer.parseBytesTry(bytes);
        assertTrue("Proposition serialization failed.", t.isSuccess());
        assertEquals("Deserialized proposition expected to be equal", addressProposition.toString(), t.get().toString());

        // Test 2: try to parse broken bytes
        boolean failureExpected = serializer.parseBytesTry("broken bytes".getBytes()).isFailure();
        assertTrue("Failure during parsing expected", failureExpected);
    }
}
