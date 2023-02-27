package com.horizen.utxo.box;

import com.horizen.fixtures.BoxFixtureClass;
import com.horizen.proposition.MCPublicKeyHashProposition;
import com.horizen.proposition.PublicKey25519Proposition;
import com.horizen.utils.Ed25519;
import com.horizen.utils.Pair;
import com.horizen.utxo.box.WithdrawalRequestBox;
import com.horizen.utxo.box.ZenBox;
import org.junit.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.Assert.*;

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
