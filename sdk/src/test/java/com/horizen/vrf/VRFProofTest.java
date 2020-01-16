package com.horizen.vrf;

import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.*;

public class VRFProofTest {

    static byte[] bhash = new byte[VRFProof.PROOF_LENGTH];;

    @BeforeClass
    public static void before() {
        System.loadLibrary("vrflib");
        for (byte i = 0; i < bhash.length; i++) bhash[i] = i;
    }

    @Test
    public void creationTest() {

        VRFProof vrfp = new VRFProof(bhash);
        assertArrayEquals("VRFProof creation error!", bhash, vrfp.bytes());

        boolean exceptionOccurred = false;
        try {
            VRFProof vrfp2 = new VRFProof(new byte[VRFProof.PROOF_LENGTH + 1]);
        }
        catch (IllegalArgumentException e) {
            exceptionOccurred = true;
        }

        assertTrue("Exception during VRFProof creation expected", exceptionOccurred);
    }

    @Test
    public void testProofToVRFHash() {

        VRFProof vrfp = new VRFProof(bhash);

        byte[] br = vrfp.proofToVRFHash();

        for (byte i = 0; i < br.length; i++)
            assertEquals("VRFHash is wrong!", br.length -i -1, br[i]);
    }
}
