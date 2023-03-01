package io.horizen.proof;

import org.junit.Test;
import scala.util.Try;

import java.util.Random;

import static org.junit.Assert.*;

public class Signature25519SerializerTest {

    @Test
    public void toBytes() {
        byte[] signatureBytes = new byte[64];
        new Random().nextBytes(signatureBytes);

        Signature25519 signature = new Signature25519(signatureBytes);
        ProofSerializer<Signature25519> serializer = signature.serializer();

        byte[] sb = serializer.toBytes(signature);
        Try<Signature25519> t = serializer.parseBytesTry(sb);

        assertEquals("Signatures are not the same.", signature, t.get());
    }
}