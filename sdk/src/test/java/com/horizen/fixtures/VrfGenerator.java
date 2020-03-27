package com.horizen.fixtures;

import com.horizen.vrf.VrfKeyGenerator;
import com.horizen.vrf.VrfProof;
import com.horizen.vrf.VrfSecretKey;

import java.util.Random;

public class VrfGenerator {
    public static VrfProof generateProof(long seed) {
        Random rnd = new Random(seed);
        byte[] vrfSeed = new byte[32];
        rnd.nextBytes(vrfSeed);
        VrfSecretKey vrfSecretKey = VrfKeyGenerator.getInstance().generateSecret(vrfSeed);
        byte[] randomBytes = new byte[32];
        rnd.nextBytes(randomBytes);
        return vrfSecretKey.prove(randomBytes);
    }
}
