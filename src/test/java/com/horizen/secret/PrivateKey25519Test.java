package com.horizen.secret;

import com.horizen.proof.ProofOfKnowledge;
import com.horizen.proposition.ProofOfKnowledgeProposition;
import com.horizen.proposition.PublicKey25519Proposition;
import org.junit.Before;
import org.junit.Test;
import scala.Tuple2;
import scorex.crypto.signatures.Curve25519;

import java.util.Random;

import static org.junit.Assert.*;

public class PrivateKey25519Test {

    byte[] testMessage = "Test string message to sign/verify.".getBytes();

    PrivateKey25519 key;
    ProofOfKnowledgeProposition<Secret> prp;
    ProofOfKnowledge<Secret, ProofOfKnowledgeProposition<Secret>> pr;

    @Before
    public void setUp() throws Exception {

        byte[] seed = new byte[32];
        new Random().nextBytes(seed);

        Tuple2<Secret, ProofOfKnowledgeProposition<Secret>> keyTuple = PrivateKey25519.generateKeys(seed);

        key = (PrivateKey25519) keyTuple._1;
        prp = keyTuple._2;
    }

    @Test
    public void secretTypeId() {
        assertEquals("Key type is invalid.", key.secretTypeId(), 0);
    }

    @Test
    public void keyLength() {
        assertEquals("Key length is invalid.", key.keyLength(), Curve25519.KeyLength());
    }

    @Test
    public void publicKeyBytes() {
    }

    @Test
    public void privateKeyBytes() {
    }

    @Test
    public void bytes() {
    }

    @Test
    public void publicImage() {
    }

    @Test
    public void owns() {
    }

    @Test
    public void sign() {
    }

    @Test
    public void verify() {
        pr = key.sign(key, testMessage);
        assertTrue("Verification of the sign filed.", key.verify(testMessage, prp, pr));
    }
}