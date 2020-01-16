package com.horizen.vrf;

import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.*;

public class VRFPublicKeyTest {

    static byte[] bkey = new byte[VRFPublicKey.KEY_LENGTH];

    @BeforeClass
    public static void before() {
        System.loadLibrary("vrflib");
        for (byte i = 0; i < bkey.length; i++) bkey[i] = i;
    }

    @Test
    public void creationTest() {

        VRFPublicKey vrfk = new VRFPublicKey(bkey);
        assertArrayEquals("VRFPublicKey creation error!", bkey, vrfk.bytes());

        boolean exceptionOccurred = false;
        try {
            VRFPublicKey vrfk2 = new VRFPublicKey(new byte[VRFPublicKey.KEY_LENGTH + 1]);
        }
        catch (IllegalArgumentException e) {
            exceptionOccurred = true;
        }

        assertTrue("Exception during VRFPublicKey creation expected", exceptionOccurred);
    }

    @Test
    public void testVerify() {

        byte[] bmessage = new byte[32];

        VRFPublicKey vrfkey = new VRFPublicKey(bkey);

        assertTrue("", vrfkey.verify(bkey, bmessage));

    }
}
