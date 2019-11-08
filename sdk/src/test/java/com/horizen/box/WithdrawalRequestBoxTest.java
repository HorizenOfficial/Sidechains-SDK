package com.horizen.box;

import com.horizen.proposition.MCPublicKeyHash;
import com.horizen.proposition.PublicKey25519Proposition;
import org.junit.Before;
import org.junit.Test;
import scala.Tuple2;
import scorex.crypto.signatures.Curve25519;

import java.util.Arrays;

import static org.junit.Assert.*;

public class WithdrawalRequestBoxTest
{
    MCPublicKeyHash proposition;
    long nonce;
    long value;

    @Before
    public void setUp() {
        byte[] anotherSeed = "testseed".getBytes();
        Tuple2<byte[], byte[]> keyPair = Curve25519.createKeyPair(anotherSeed);
        proposition = new MCPublicKeyHash(new byte[20]);

        nonce = 12345;
        value = 10;
    }

    @Test
    public void WithdrawalRequestBox_CreationTest() {
        WithdrawalRequestBox box = new WithdrawalRequestBox(proposition, nonce, value);

        assertTrue("WithdrawalRequestBox creation: proposition is wrong", proposition.equals(box.proposition));
        assertTrue("WithdrawalRequestBox creation: nonce is wrong", box.nonce() == nonce);
        assertTrue("WithdrawalRequestBox creation: value is wrong", box.value() == value);
    }

    @Test
    public void WithdrawalRequestBox_ComparisonTest() {
        WithdrawalRequestBox box1 = new WithdrawalRequestBox(proposition, nonce, value);
        WithdrawalRequestBox box2 = new WithdrawalRequestBox(proposition, nonce, value);

        assertTrue("Boxes hash codes expected to be equal", box1.hashCode() == box2.hashCode());
        assertTrue("Boxes expected to be equal", box1.equals(box2));
        assertTrue("Boxes ids expected to be equal", Arrays.equals(box1.id(), box2.id()));


        byte[] anotherSeed = "another test seed".getBytes();
        Tuple2<byte[], byte[]> keyPair = Curve25519.createKeyPair(anotherSeed);
        byte[] mcKey1 = new byte[MCPublicKeyHash.KEY_LENGTH];
        Arrays.fill(mcKey1, (byte) 1);
        MCPublicKeyHash anotherProposition = new MCPublicKeyHash(mcKey1);

        WithdrawalRequestBox box3 = new WithdrawalRequestBox(anotherProposition, nonce, value);
        assertFalse("Boxes hash codes expected to be different", box1.hashCode() == box3.hashCode());
        assertFalse("Boxes expected to be different", box1.equals(box3));
        assertFalse("Boxes ids expected to be different", Arrays.equals(box1.id(), box3.id()));


        WithdrawalRequestBox box4 = new WithdrawalRequestBox(proposition, nonce + 1, value);
        assertTrue("Boxes hash codes expected to be equal", box1.hashCode() == box4.hashCode());
        assertFalse("Boxes expected to be different", box1.equals(box4));
        assertFalse("Boxes ids expected to be different", Arrays.equals(box1.id(), box4.id()));


        WithdrawalRequestBox box5 = new WithdrawalRequestBox(proposition, nonce, value + 1);
        assertTrue("Boxes hash codes expected to be equal", box1.hashCode() == box5.hashCode());
        assertFalse("Boxes expected to be different", box1.equals(box5));
        assertTrue("Boxes ids expected to be equal", Arrays.equals(box1.id(), box5.id()));
    }
}