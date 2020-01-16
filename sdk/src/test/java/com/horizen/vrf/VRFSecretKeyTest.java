package com.horizen.vrf;

import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.*;

public class VRFSecretKeyTest {

    static byte[] bkey = new byte[VRFSecretKey.KEY_LENGTH];

    @BeforeClass
    public static void before() {
        System.loadLibrary("vrflib");
        for (byte i = 0; i < bkey.length; i++) bkey[i] = i;
    }

    @Test
    public void creationTest() {

        VRFSecretKey vrfk = new VRFSecretKey(bkey);
        assertArrayEquals("VRFSecretKey creation error!", bkey, vrfk.bytes());

        boolean exceptionOccurred = false;
        try {
            VRFSecretKey vrfk2 = new VRFSecretKey(new byte[VRFSecretKey.KEY_LENGTH + 1]);
        }
        catch (IllegalArgumentException e) {
            exceptionOccurred = true;
        }

        assertTrue("Exception during VRFPublicKey creation expected", exceptionOccurred);
    }

    @Test
    public void testProove() {

        byte[] bmessage = new byte[32];

        for (byte i = 0; i < bmessage.length; i++) bmessage[i] = i;

        VRFSecretKey vrfsk = new VRFSecretKey(bkey);

        VRFProof r = vrfsk.prove(bmessage);

        for (byte i = 0; i < r.PROOF_LENGTH; i++)
            assertEquals("VRFProof is wrong!", r.PROOF_LENGTH -i -1, r.bytes()[i]);
    }

    @Test
    public void testVRFHash() {

        byte[] bkey = new byte[32];
        byte[] bmessage = new byte[32];

        for (byte i = 0; i < bmessage.length; i++) bmessage[i] = i;

        VRFSecretKey vrfsk = new VRFSecretKey(bkey);

        byte[] br = vrfsk.vrfHash(bmessage);

        for (byte i = 0; i < br.length; i++)
            assertEquals("VRFHash is wrong!", br.length -i -1, br[i]);
    }
}
