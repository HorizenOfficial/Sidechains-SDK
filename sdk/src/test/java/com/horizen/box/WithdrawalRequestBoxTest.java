package com.horizen.box;

import com.horizen.fixtures.BoxFixtureClass;
import com.horizen.proposition.MCPublicKeyHashProposition;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.*;

public class WithdrawalRequestBoxTest extends BoxFixtureClass
{
    MCPublicKeyHashProposition proposition;
    long nonce;
    long value;

    @Before
    public void setUp() {
        proposition = new MCPublicKeyHashProposition(new byte[20]);

        nonce = 12345;
        value = 10;
    }

    @Test
    public void creationTest() {
        WithdrawalRequestBox box = getWithdrawalRequestBox(proposition, nonce, value);

        assertEquals("WithdrawalRequestBox creation: proposition is wrong", proposition, box.proposition());
        assertEquals("WithdrawalRequestBox creation: nonce is wrong", box.nonce(), nonce);
        assertEquals("WithdrawalRequestBox creation: value is wrong", box.value(), value);
    }

    @Test
    public void comparisonTest() {
        WithdrawalRequestBox box1 = getWithdrawalRequestBox(proposition, nonce, value);
        WithdrawalRequestBox box2 = getWithdrawalRequestBox(proposition, nonce, value);

        assertEquals("Boxes hash codes expected to be equal", box1.hashCode(), box2.hashCode());
        assertEquals("Boxes expected to be equal", box1, box2);
        assertTrue("Boxes ids expected to be equal", Arrays.equals(box1.id(), box2.id()));


        byte[] anotherSeed = "another test seed".getBytes();
        byte[] mcKey1 = new byte[MCPublicKeyHashProposition.KEY_LENGTH];
        Arrays.fill(mcKey1, (byte) 1);
        MCPublicKeyHashProposition anotherProposition = new MCPublicKeyHashProposition(mcKey1);

        WithdrawalRequestBox box3 = getWithdrawalRequestBox(anotherProposition, nonce, value);
        assertNotEquals("Boxes hash codes expected to be different", box1.hashCode(), box3.hashCode());
        assertNotEquals("Boxes expected to be different", box1, box3);
        assertFalse("Boxes ids expected to be different", Arrays.equals(box1.id(), box3.id()));


        WithdrawalRequestBox box4 = getWithdrawalRequestBox(proposition, nonce + 1, value);
        assertNotEquals("Boxes hash codes expected to be different", box1.hashCode(), box4.hashCode());
        assertNotEquals("Boxes expected to be different", box1, box4);
        assertFalse("Boxes ids expected to be different", Arrays.equals(box1.id(), box4.id()));


        WithdrawalRequestBox box5 = getWithdrawalRequestBox(proposition, nonce, value + 1);
        assertNotEquals("Boxes hash codes expected to be different", box1.hashCode(), box5.hashCode());
        assertNotEquals("Boxes expected to be different", box1, box5);
        assertFalse("Boxes ids expected to be different", Arrays.equals(box1.id(), box5.id()));
    }
}