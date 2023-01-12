package com.horizen.account.proposition;

import com.horizen.account.secret.PrivateKeySecp256k1;
import com.horizen.account.secret.PrivateKeySecp256k1Creator;
import com.horizen.proposition.PropositionSerializer;
import org.junit.Before;
import org.junit.Test;
import scala.util.Try;

import static org.junit.Assert.assertTrue;

public class AddressPropositionSerializerTest {
    AddressProposition addressProposition;

    @Before
    public void BeforeEachTest() {
        PrivateKeySecp256k1 privateKey = PrivateKeySecp256k1Creator.getInstance().generateSecret("addressproptest".getBytes());
        addressProposition = privateKey.publicImage();
    }

    @Test
    public void addressPropositionSerializeTest() {
        // Get proposition serializer and serialize
        PropositionSerializer serializer = addressProposition.serializer();
        byte[] bytes = serializer.toBytes(addressProposition);

        // Test 1: Correct bytes deserialization
        Try<AddressProposition> t = serializer.parseBytesTry(bytes);
        assertTrue("Deserialized proposition expected to be equal, but was " + t.get().toString() + " instead of " + addressProposition.toString(),
                addressProposition.equals(t.get()));

        // Test 2: try to parse broken bytes
        boolean failureExpected = serializer.parseBytesTry("broken bytes".getBytes()).isFailure();
        assertTrue("Failure during parsing expected", failureExpected);
    }
}
