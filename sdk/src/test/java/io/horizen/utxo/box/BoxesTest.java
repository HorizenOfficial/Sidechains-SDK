package io.horizen.utxo.box;

import io.horizen.proposition.MCPublicKeyHashProposition;
import io.horizen.proposition.PublicKey25519Proposition;
import io.horizen.utils.Ed25519;
import io.horizen.utils.Pair;
import io.horizen.utxo.fixtures.BoxFixtureClass;
import org.junit.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertNotEquals;

public class BoxesTest extends BoxFixtureClass {

    @Test
    public void differentBoxTypesComparisonTest() {
        byte[] anotherSeed = "testseed".getBytes(StandardCharsets.UTF_8);
        Pair<byte[], byte[]> keyPair = Ed25519.createKeyPair(anotherSeed);
        PublicKey25519Proposition proposition = new PublicKey25519Proposition(keyPair.getValue());
        MCPublicKeyHashProposition mcPublicKeyHashProposition = getMCPublicKeyHashProposition();

        long nonce = 1234;
        long value = 0;

        // Boxes has the same proposition, nonce and value
        ZenBox zenBox = getZenBox(proposition, nonce, value);
        WithdrawalRequestBox withdrawalRequestBox = getWithdrawalRequestBox(mcPublicKeyHashProposition, nonce, value);

        assertNotEquals("Boxes expected to have different type ids", zenBox.boxTypeId(), withdrawalRequestBox.boxTypeId());
        assertNotEquals("Boxes expected to have different hash", zenBox.hashCode(), withdrawalRequestBox.hashCode());
        assertNotEquals("Boxes expected not to be equal", zenBox, withdrawalRequestBox);
    }
}
