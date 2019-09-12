package com.horizen.proposition;

import io.circe.Json;
import org.junit.Before;
import org.junit.Test;

import scala.Tuple2;
import scorex.crypto.signatures.Curve25519;

import static org.junit.Assert.*;

public class PublicKey25519PropositionTest {

    byte[] seed;

    byte[] publicKey;
    byte[] privateKey;

    byte[] messageToSign;
    byte[] signature;


    @Before
    public void beforeEachTest() {
        seed = "12345".getBytes();
        Tuple2<byte[], byte[]> keyPair = Curve25519.createKeyPair(seed);
        privateKey = keyPair._1();
        publicKey = keyPair._2();
        messageToSign = "message to sign".getBytes();
        signature = Curve25519.sign(privateKey, messageToSign);
    }


    @Test
    public void PublicKey25519Proposition_CreationTest() {

        PublicKey25519Proposition prop1 = new PublicKey25519Proposition(publicKey);
        assertArrayEquals("Exception during PublicKey25519Proposition creation", publicKey, prop1.pubKeyBytes());


        boolean exceptionOccurred = false;
        try {
            PublicKey25519Proposition prop2 = new PublicKey25519Proposition("broken public key".getBytes());
        }
        catch (IllegalArgumentException e) {
            exceptionOccurred = true;
        }

        assertEquals("Exception during PublicKey25519Proposition creation expected", true, exceptionOccurred);
    }

    @Test
    public void PublicKey25519Proposition_ComparisonTest() {
        PublicKey25519Proposition prop1 = new PublicKey25519Proposition(publicKey);
        PublicKey25519Proposition prop2 = new PublicKey25519Proposition(publicKey);

        assertEquals("Propositions hash codes expected to be equal", true, prop1.hashCode() == prop2.hashCode());
        assertEquals("Propositions expected to be equal", true, prop1.equals(prop2));

        byte[] anotherSeed = "testseed".getBytes();
        Tuple2<byte[], byte[]> keyPair = Curve25519.createKeyPair(anotherSeed);
        PublicKey25519Proposition prop3 = new PublicKey25519Proposition(keyPair._2());

        assertEquals("Propositions hash codes expected to be different", false, prop1.hashCode() == prop3.hashCode());
        assertEquals("Propositions expected to be different", false, prop1.equals(prop3));
    }

    @Test
    public void PublicKey25519Proposition_AddressTest() {
        PublicKey25519Proposition prop1 = new PublicKey25519Proposition(publicKey);
        String encodedAddress = prop1.address();
        byte[] decodedAddress = PublicKey25519Proposition.encoder().decode(encodedAddress).get();
        assertEquals("Another address length expected", PublicKey25519Proposition.ADDRESS_LENGTH, decodedAddress.length);

        PublicKey25519Proposition prop2 = PublicKey25519Proposition.parseAddress(encodedAddress);

        assertNotNull(prop2);
        assertEquals("Propositions hash codes expected to be equal", true, prop1.hashCode() == prop2.hashCode());
        assertEquals("Propositions expected to be equal", true, prop1.equals(prop2));
    }

    @Test
    public void PublicKey25519Proposition_VerifyTest() {
        PublicKey25519Proposition prop1 = new PublicKey25519Proposition(publicKey);

        boolean res = prop1.verify(messageToSign, signature);
        assertEquals("Signature expected to be valid", true, res);

        res = prop1.verify("another message".getBytes(), signature);
        assertEquals("Signature expected to be NOT valid", false, res);

        res = prop1.verify(messageToSign, "broken signature".getBytes());
        assertEquals("Signature expected to be NOT valid", false, res);


        byte[] anotherSeed = "testseed".getBytes();
        Tuple2<byte[], byte[]> keyPair = Curve25519.createKeyPair(anotherSeed);
        PublicKey25519Proposition prop2 = new PublicKey25519Proposition(keyPair._2());

        res = prop2.verify(messageToSign, signature);
        assertEquals("Signature expected to be NOT valid", false, res);
    }
}