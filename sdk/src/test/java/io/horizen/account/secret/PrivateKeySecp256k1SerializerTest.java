package io.horizen.account.secret;

import io.horizen.secret.SecretSerializer;
import org.junit.Before;
import org.junit.Test;
import scala.util.Try;
import java.nio.charset.StandardCharsets;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class PrivateKeySecp256k1SerializerTest {
    PrivateKeySecp256k1 privateKeySecp256k1;

    @Before
    public void BeforeEachTest() {
        privateKeySecp256k1 = PrivateKeySecp256k1Creator.getInstance().generateSecret("secpprivatekeytest".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    public void privateKeySecp256k1SerializeTest() {
        // Get secret serializer and serialize
        SecretSerializer serializer = privateKeySecp256k1.serializer();
        byte[] bytes = serializer.toBytes(privateKeySecp256k1);

        // Test 1: Correct bytes deserialization
        Try<PrivateKeySecp256k1> t = serializer.parseBytesTry(bytes);
        assertTrue("Secret serialization failed.", t.isSuccess());
        assertEquals("Deserialized secret expected to be equal", privateKeySecp256k1.toString(), t.get().toString());

        // Test 2: try to parse broken bytes
        boolean failureExpected = serializer.parseBytesTry("broken bytes".getBytes(StandardCharsets.UTF_8)).isFailure();
        assertTrue("Failure during parsing expected", failureExpected);
    }
}
