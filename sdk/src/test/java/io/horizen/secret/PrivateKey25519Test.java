package io.horizen.secret;

import io.horizen.proposition.PublicKey25519Proposition;
import io.horizen.proof.Signature25519;
import org.junit.Before;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.Random;

import static org.junit.Assert.*;

public class PrivateKey25519Test {
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
    }

    @Test
    public void secretTypeId() {
        assertEquals("Key type is invalid.", key.secretTypeId(), 0);
    }

    @Test
    public void bytes() {
    }

    @Test
    public void publicImage() {
        assertEquals("Public keys proposition aren't the same.", prp, key.publicImage());
    }

    @Test
    public void owns() {
    }

    @Test
    public void verify() {
        pr = key.sign(testMessage);
        assertTrue("Verification of the sign filed.", pr.isValid(prp, testMessage));
    }
}