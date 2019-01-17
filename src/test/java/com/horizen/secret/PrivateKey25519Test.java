package com.horizen.secret;

import com.horizen.proposition.PublicKey25519Proposition;
import com.horizen.proof.Signature25519;
import org.junit.Before;
import org.junit.Test;
import scala.Tuple2;

import java.util.Random;

import static org.junit.Assert.*;

public class PrivateKey25519Test {


    byte[] testMessage = "Test string message to sign/verify.".getBytes();

    PrivateKey25519 key;
    PublicKey25519Proposition prp;
    Signature25519 pr;

    @Before
    public void setUp() throws Exception {

        byte[] seed = new byte[32];
        new Random().nextBytes(seed);

        Tuple2<PrivateKey25519, PublicKey25519Proposition> keyTuple = PrivateKey25519.generateKeys(seed);

        key = (PrivateKey25519) keyTuple._1;
        prp = keyTuple._2;
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
        assertTrue("Verification of the sign filed.", key.verify(testMessage, prp, pr));
    }
}