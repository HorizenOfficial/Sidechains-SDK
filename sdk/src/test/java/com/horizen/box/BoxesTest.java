package com.horizen.box;

import com.horizen.proposition.PublicKey25519Proposition;
import com.horizen.utils.Ed25519;
import com.horizen.utils.Pair;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class BoxesTest {

    @Test
    public void BoxesTest_DifferentBoxTypesComparisonTest() {
        byte[] anotherSeed = "testseed".getBytes();
        Pair<byte[], byte[]> keyPair = Ed25519.createKeyPair(anotherSeed);
        PublicKey25519Proposition proposition = new PublicKey25519Proposition(keyPair.getValue());

        long nonce = 1234;
        long value = 0;
        long minimumWithdrawalEpoch = 1;

        // Boxes has the same proposition, nonce and value (value if any CertifierRightBox is 0)
        RegularBox regularBox = new RegularBox(proposition, nonce, value);
        CertifierRightBox certifierRightBox = new CertifierRightBox(proposition, nonce, value, minimumWithdrawalEpoch);

        assertEquals("Boxes expected to have different type ids", false, regularBox.boxTypeId() == certifierRightBox.boxTypeId());
        assertEquals("Boxes expected to have the same hash", regularBox.hashCode(), certifierRightBox.hashCode());
        assertEquals("Boxes expected not to be equal", false, regularBox.equals(certifierRightBox));
    }
}
