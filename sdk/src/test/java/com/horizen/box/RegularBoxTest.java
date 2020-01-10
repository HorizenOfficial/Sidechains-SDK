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
    public void RegularBox_CreationTest() {
        RegularBox box = getRegularBox(proposition, nonce, value);

        assertEquals("RegularBox creation: proposition is wrong", true, box.proposition() == proposition);
        assertEquals("RegularBox creation: nonce is wrong", true, box.nonce() == nonce);
        assertEquals("RegularBox creation: value is wrong", true, box.value() == value);
    }

    @Test
    public void RegularBox_ComparisonTest() {
        RegularBox box1 = getRegularBox(proposition, nonce, value);
        RegularBox box2 = getRegularBox(proposition, nonce, value);

        assertEquals("Boxes hash codes expected to be equal", true, box1.hashCode() == box2.hashCode());
        assertEquals("Boxes expected to be equal", true, box1.equals(box2));
        assertEquals("Boxes ids expected to be equal", true, Arrays.equals(box1.id(), box2.id()));


        byte[] anotherSeed = "another test seed".getBytes();
        Pair<byte[], byte[]> keyPair = Ed25519.createKeyPair(anotherSeed);
        PublicKey25519Proposition anotherProposition = new PublicKey25519Proposition(keyPair.getValue());

        RegularBox box3 = getRegularBox(anotherProposition, nonce, value);
        assertEquals("Boxes hash codes expected to be different", false, box1.hashCode() == box3.hashCode());
        assertEquals("Boxes expected to be different", false, box1.equals(box3));
        assertEquals("Boxes ids expected to be different", false, Arrays.equals(box1.id(), box3.id()));


        RegularBox box4 = getRegularBox(proposition, nonce + 1, value);
        assertEquals("Boxes hash codes expected to be equal", true, box1.hashCode() == box4.hashCode());
        assertEquals("Boxes expected to be different", false, box1.equals(box4));
        assertEquals("Boxes ids expected to be different", false, Arrays.equals(box1.id(), box4.id()));


        RegularBox box5 = getRegularBox(proposition, nonce, value + 1);
        assertEquals("Boxes hash codes expected to be equal", true, box1.hashCode() == box5.hashCode());
        assertEquals("Boxes expected to be different", false, box1.equals(box5));
        assertEquals("Boxes ids expected to be equal", true, Arrays.equals(box1.id(), box5.id()));
    }
}