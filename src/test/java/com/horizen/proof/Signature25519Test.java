package com.horizen.proof;

import com.horizen.proposition.PublicKey25519Proposition;
import com.horizen.secret.PrivateKey25519;
import com.horizen.proof.Signature25519;
import org.junit.Before;
import org.junit.Test;
import scala.Tuple2;

import java.util.Random;

import static org.junit.Assert.*;

public class Signature25519Test {

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
        pr = key.sign(testMessage);
    }

    @Test
    public void isValid() {
        assertTrue("Signature is invalid.", pr.isValid(prp, testMessage));
    }
}