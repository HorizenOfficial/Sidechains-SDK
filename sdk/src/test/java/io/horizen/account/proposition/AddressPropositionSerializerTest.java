package io.horizen.account.proposition;

import io.horizen.account.secret.PrivateKeySecp256k1;
import io.horizen.account.secret.PrivateKeySecp256k1Creator;
import io.horizen.proposition.PropositionSerializer;
import org.junit.Before;
import org.junit.Test;
import scala.util.Try;

import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertTrue;

public class AddressPropositionSerializerTest {
    AddressProposition addressProposition;

    @Before
    public void BeforeEachTest() {
        PrivateKeySecp256k1 privateKey = PrivateKeySecp256k1Creator.getInstance().generateSecret("addressproptest".getBytes(StandardCharsets.UTF_8));
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
        boolean failureExpected = serializer.parseBytesTry("broken bytes".getBytes(StandardCharsets.UTF_8)).isFailure();
        assertTrue("Failure during parsing expected", failureExpected);
    }
}
