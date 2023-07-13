package io.horizen.proposition;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class MCPublicKeyHashPropositionTest {

    byte[] publicKeyHash;
    byte[] brokenPublicKeyHash;

    @Before
    public void beforeEachTest() {
        publicKeyHash = new byte[MCPublicKeyHashProposition.KEY_LENGTH];
        brokenPublicKeyHash = new byte[MCPublicKeyHashProposition.KEY_LENGTH + 1];
    }


    @Test
    public void creationTest() {

        MCPublicKeyHashProposition prop1 = new MCPublicKeyHashProposition(publicKeyHash);
        assertArrayEquals("Exception during MCPublicKeyHashProposition creation", publicKeyHash, prop1.bytes());

        boolean exceptionOccurred = false;
        try {
            MCPublicKeyHashProposition prop2 = new MCPublicKeyHashProposition(brokenPublicKeyHash);
        }
        catch (IllegalArgumentException e) {
            exceptionOccurred = true;
        }

        assertEquals("Exception during MCPublicKeyHashProposition creation expected", true, exceptionOccurred);
    }

}