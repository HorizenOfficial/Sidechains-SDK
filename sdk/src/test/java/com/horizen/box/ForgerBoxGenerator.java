package com.horizen.box;

import com.horizen.proposition.PublicKey25519Proposition;
import com.horizen.utils.Ed25519;
import com.horizen.utils.Pair;
import com.horizen.vrf.VRFKeyGenerator;
import com.horizen.vrf.VRFPublicKey;

import java.util.Random;

public class ForgerBoxGenerator {
    public static ForgerBox generateForgerBox(int seed) {
        Random randomGenerator = new Random(seed);
        byte[] byteSeed = new byte[32];
        randomGenerator.nextBytes(byteSeed);

        Pair<byte[], byte[]> propositionKeyPair = Ed25519.createKeyPair(byteSeed);
        PublicKey25519Proposition proposition = new PublicKey25519Proposition(propositionKeyPair.getValue());
        long nonce = randomGenerator.nextLong();
        long value = randomGenerator.nextLong();
        Pair<byte[], byte[]> rewardKeyPair = Ed25519.createKeyPair(propositionKeyPair.getKey());
        PublicKey25519Proposition rewardProposition = new PublicKey25519Proposition(rewardKeyPair.getValue());

        VRFPublicKey vrfPubKey = VRFKeyGenerator.generate(rewardKeyPair.getKey()).getValue();
        return new ForgerBox(proposition, nonce, value, rewardProposition, vrfPubKey);
    }

    public static ForgerBox generateForgerBox() {
        return generateForgerBox(new Random().nextInt());
    }
}
