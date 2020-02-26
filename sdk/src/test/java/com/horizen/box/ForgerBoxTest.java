package com.horizen.box;

import com.horizen.fixtures.ForgerBoxFixture;
import com.horizen.fixtures.BoxFixtureClass;
import com.horizen.proposition.PublicKey25519Proposition;
import com.horizen.utils.Ed25519;
import com.horizen.utils.Pair;
import com.horizen.vrf.VRFKeyGenerator;
import com.horizen.vrf.VRFPublicKey;
import org.junit.Test;
import java.util.Random;

import static org.junit.Assert.*;

public class ForgerBoxTest extends BoxFixtureClass {

    @Test
    public void getterTest() {
        Random randomGenerator = new Random(42);
        byte[] byteSeed = new byte[32];
        randomGenerator.nextBytes(byteSeed);

        Pair<byte[], byte[]> propositionKeyPair = Ed25519.createKeyPair(byteSeed);
        PublicKey25519Proposition proposition = new PublicKey25519Proposition(propositionKeyPair.getValue());
        long nonce = randomGenerator.nextLong();
        long value = randomGenerator.nextLong();
        Pair<byte[], byte[]> rewardKeyPair = Ed25519.createKeyPair(propositionKeyPair.getKey());
        PublicKey25519Proposition rewardProposition = new PublicKey25519Proposition(rewardKeyPair.getValue());

        VRFPublicKey vrfPubKey = VRFKeyGenerator.generate(rewardKeyPair.getKey())._2();
        ForgerBox box = getForgerBox(proposition, nonce, value, rewardProposition, vrfPubKey);

        assertEquals("Proposition shall be equal", proposition, box.proposition());
        assertEquals("Nonce shall be equal", nonce, box.nonce());
        assertEquals("value shall be equal", value, box.value());
        assertEquals("rewardProposition shall be equal", rewardProposition, box.rewardProposition());
        assertEquals("Nonce shall be equal", vrfPubKey, box.vrfPubKey());
    }

    @Test
    public void equalsAndHashTest() {
        ForgerBox left = ForgerBoxFixture.generateForgerBox(1)._1;
        ForgerBox sameAsLeft = getForgerBox(left.proposition(),  left.nonce(), left.value(), left.rewardProposition(), left.vrfPubKey());
        ForgerBox right = ForgerBoxFixture.generateForgerBox(2)._1;

        assertEquals("Forger boxes with same data shall be equals", left, sameAsLeft);
        assertNotEquals("Forger boxes with same data shall be the same", left, right);
        assertEquals("Hash for Forger boxes with same data shall have same hash", left.hashCode(), sameAsLeft.hashCode());
    }

    private void checkSerialization(int seed) {
        ForgerBox initial = ForgerBoxFixture.generateForgerBox(seed)._1;

        byte[] serialized = initial.bytes();
        ForgerBox parsed = ForgerBox.parseBytes(serialized);

        assertEquals(initial, parsed);
    }

    @Test
    public void serializationTest() {
        Random randomGenerator = new Random(32);
        for (int i = 0; i < 10; i++) {
            checkSerialization(randomGenerator.nextInt());
        }
    }

}