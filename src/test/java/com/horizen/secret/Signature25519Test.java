package com.horizen.secret;

import com.horizen.proof.ProofOfKnowledge;
import com.horizen.proposition.ProofOfKnowledgeProposition;
import org.junit.Before;
import org.junit.Test;
import scala.Tuple2;

import java.util.Random;

import static org.junit.Assert.*;

public class Signature25519Test {

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
        pr = key.sign(key, testMessage);
    }

    @Test
    public void isValid() {
        assertTrue("Signature is invalid.", pr.isValid(prp, testMessage));
    }
}