package io.horizen.proof;

import io.horizen.proposition.PublicKey25519Proposition;
import io.horizen.secret.PrivateKey25519;
import io.horizen.secret.PrivateKey25519Creator;
import org.junit.Before;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.Random;

import static org.junit.Assert.*;

public class Signature25519Test {

    byte[] testMessage = "Test string message to sign/verify.".getBytes(StandardCharsets.UTF_8);

    PrivateKey25519 key;
    PublicKey25519Proposition prp;
    Signature25519 pr;

    @Before
    public void setUp() throws Exception {
        byte[] seed = new byte[32];
        new Random().nextBytes(seed);

        key = PrivateKey25519Creator.getInstance().generateSecret(seed);
        prp = key.publicImage();
        pr = key.sign(testMessage);
    }

    @Test
    public void isValid() {
        assertTrue("Signature is invalid.", pr.isValid(prp, testMessage));
    }
}