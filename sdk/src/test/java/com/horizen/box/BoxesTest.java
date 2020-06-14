package com.horizen.box;

import com.horizen.fixtures.BoxFixtureClass;
import com.horizen.proposition.PublicKey25519Proposition;
import com.horizen.utils.Ed25519;
import com.horizen.utils.Pair;
import org.junit.Test;

import static org.junit.Assert.*;

public class BoxesTest extends BoxFixtureClass {

    @Test
    public void differentBoxTypesComparisonTest() {
        byte[] anotherSeed = "testseed".getBytes();
        Pair<byte[], byte[]> keyPair = Ed25519.createKeyPair(anotherSeed);
        PublicKey25519Proposition proposition = new PublicKey25519Proposition(keyPair.getValue());

        long nonce = 1234;
        long value = 0;
        long minimumWithdrawalEpoch = 1;

        // Boxes has the same proposition, nonce and value
        RegularBox regularBox = getRegularBox(proposition, nonce, value);
        CertifierRightBox certifierRightBox = getCertifierRightBox(proposition, nonce, value, minimumWithdrawalEpoch);

        assertNotEquals("Boxes expected to have different type ids", regularBox.boxTypeId(), certifierRightBox.boxTypeId());
        assertNotEquals("Boxes expected to have different hash", regularBox.hashCode(), certifierRightBox.hashCode());
        assertNotEquals("Boxes expected not to be equal", regularBox, certifierRightBox);
    }
}
