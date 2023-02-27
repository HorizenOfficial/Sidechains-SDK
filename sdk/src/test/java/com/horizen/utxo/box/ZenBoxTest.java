package com.horizen.utxo.box;

import com.horizen.utxo.fixtures.BoxFixtureClass;
import com.horizen.proposition.PublicKey25519Proposition;
import com.horizen.utils.Ed25519;
import com.horizen.utils.Pair;
import org.junit.Before;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.junit.Assert.*;

public class ZenBoxTest extends BoxFixtureClass
{
    PublicKey25519Proposition proposition;
    long nonce;
    long value;

    @Before
    public void setUp() {
        byte[] anotherSeed = "testseed".getBytes(StandardCharsets.UTF_8);
        Pair<byte[], byte[]> keyPair = Ed25519.createKeyPair(anotherSeed);
        proposition = new PublicKey25519Proposition(keyPair.getValue());

        nonce = 12345;
        value = 10;
    }

    @Test
    public void creationTest() {
        ZenBox box = getZenBox(proposition, nonce, value);

        assertEquals("ZenBox creation: proposition is wrong", box.proposition(), proposition);
        assertEquals("ZenBox creation: nonce is wrong", box.nonce(), nonce);
        assertEquals("ZenBox creation: value is wrong", box.value(), value);
    }

    @Test
    public void comparisonTest() {
        ZenBox box1 = getZenBox(proposition, nonce, value);
        ZenBox box2 = getZenBox(proposition, nonce, value);

        assertEquals("Boxes hash codes expected to be equal", box1.hashCode(), box2.hashCode());
        assertEquals("Boxes expected to be equal", box1, box2);
        assertTrue("Boxes ids expected to be equal", Arrays.equals(box1.id(), box2.id()));


        byte[] anotherSeed = "another test seed".getBytes(StandardCharsets.UTF_8);
        Pair<byte[], byte[]> keyPair = Ed25519.createKeyPair(anotherSeed);
        PublicKey25519Proposition anotherProposition = new PublicKey25519Proposition(keyPair.getValue());

        ZenBox box3 = getZenBox(anotherProposition, nonce, value);
        assertNotEquals("Boxes hash codes expected to be different", box1.hashCode(), box3.hashCode());
        assertNotEquals("Boxes expected to be different", box1, box3);
        assertFalse("Boxes ids expected to be different", Arrays.equals(box1.id(), box3.id()));


        ZenBox box4 = getZenBox(proposition, nonce + 1, value);
        assertNotEquals("Boxes hash codes expected to be different", box1.hashCode(), box4.hashCode());
        assertNotEquals("Boxes expected to be different", box1, box4);
        assertFalse("Boxes ids expected to be different", Arrays.equals(box1.id(), box4.id()));


        ZenBox box5 = getZenBox(proposition, nonce, value + 1);
        assertNotEquals("Boxes hash codes expected to be different", box1.hashCode(), box5.hashCode());
        assertNotEquals("Boxes expected to be different", box1, box5);
        assertFalse("Boxes ids expected to be different", Arrays.equals(box1.id(), box5.id()));
    }
}