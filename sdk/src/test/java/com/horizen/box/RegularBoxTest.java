package com.horizen.box;

import com.horizen.fixtures.BoxFixtureClass;
import com.horizen.proposition.PublicKey25519Proposition;
import com.horizen.utils.Ed25519;
import com.horizen.utils.Pair;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.*;

public class RegularBoxTest extends BoxFixtureClass
{
    PublicKey25519Proposition proposition;
    long nonce;
    long value;

    @Before
    public void setUp() {
        byte[] anotherSeed = "testseed".getBytes();
        Pair<byte[], byte[]> keyPair = Ed25519.createKeyPair(anotherSeed);
        proposition = new PublicKey25519Proposition(keyPair.getValue());

        nonce = 12345;
        value = 10;
    }

    @Test
    public void creationTest() {
        RegularBox box = getRegularBox(proposition, nonce, value);

        assertEquals("RegularBox creation: proposition is wrong", box.proposition(), proposition);
        assertEquals("RegularBox creation: nonce is wrong", box.nonce(), nonce);
        assertEquals("RegularBox creation: value is wrong", box.value(), value);
    }

    @Test
    public void comparisonTest() {
        RegularBox box1 = getRegularBox(proposition, nonce, value);
        RegularBox box2 = getRegularBox(proposition, nonce, value);

        assertEquals("Boxes hash codes expected to be equal", box1.hashCode(), box2.hashCode());
        assertEquals("Boxes expected to be equal", box1, box2);
        assertTrue("Boxes ids expected to be equal", Arrays.equals(box1.id(), box2.id()));


        byte[] anotherSeed = "another test seed".getBytes();
        Pair<byte[], byte[]> keyPair = Ed25519.createKeyPair(anotherSeed);
        PublicKey25519Proposition anotherProposition = new PublicKey25519Proposition(keyPair.getValue());

        RegularBox box3 = getRegularBox(anotherProposition, nonce, value);
        assertNotEquals("Boxes hash codes expected to be different", box1.hashCode(), box3.hashCode());
        assertNotEquals("Boxes expected to be different", box1, box3);
        assertFalse("Boxes ids expected to be different", Arrays.equals(box1.id(), box3.id()));


        RegularBox box4 = getRegularBox(proposition, nonce + 1, value);
        assertNotEquals("Boxes hash codes expected to be different", box1.hashCode(), box4.hashCode());
        assertNotEquals("Boxes expected to be different", box1, box4);
        assertFalse("Boxes ids expected to be different", Arrays.equals(box1.id(), box4.id()));


        RegularBox box5 = getRegularBox(proposition, nonce, value + 1);
        assertNotEquals("Boxes hash codes expected to be different", box1.hashCode(), box5.hashCode());
        assertNotEquals("Boxes expected to be different", box1, box5);
        assertFalse("Boxes ids expected to be different", Arrays.equals(box1.id(), box5.id()));
    }
}