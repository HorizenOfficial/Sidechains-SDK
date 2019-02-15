package com.horizen.box;

import com.horizen.proposition.PublicKey25519Proposition;
import org.junit.Test;
import scala.Tuple2;
import scorex.crypto.signatures.Curve25519;

import static org.junit.Assert.assertEquals;

public class BoxesTest {

    @Test
    public void BoxesTest_DifferentBoxTypesComparisonTest() {
        byte[] anotherSeed = "testseed".getBytes();
        Tuple2<byte[], byte[]> keyPair = Curve25519.createKeyPair(anotherSeed);
        PublicKey25519Proposition proposition = new PublicKey25519Proposition(keyPair._2());

        long nonce = 1234;
        long value = 0;
        long minimumWithdrawalEpoch = 1;

        // Boxes has the same proposition, nonce and value (value if any CertifierRightBox is 0)
        RegularBox regularBox = new RegularBox(proposition, nonce, value);
        CertifierRightBox certifierRightBox = new CertifierRightBox(proposition, nonce, minimumWithdrawalEpoch);

        assertEquals("Boxes expected to have different type ids", false, regularBox.boxTypeId() == certifierRightBox.boxTypeId());
        assertEquals("Boxes expected to have the same hash", regularBox.hashCode(), certifierRightBox.hashCode());
        assertEquals("Boxes expected not to be equal", false, regularBox.equals(certifierRightBox));
    }
}
