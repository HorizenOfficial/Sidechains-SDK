package com.horizen.fixtures;

import com.horizen.secret.VrfKeyGenerator;
import com.horizen.proof.VrfProof;
import com.horizen.secret.VrfSecretKey;
import com.horizen.vrf.VrfProofHash;

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

    public static VrfProofHash generateProofHash(long seed) {
        Random rnd = new Random(seed);
        byte[] vrfSeed = new byte[32];
        rnd.nextBytes(vrfSeed);
        VrfSecretKey vrfSecretKey = VrfKeyGenerator.getInstance().generateSecret(vrfSeed);
        byte[] randomBytes = new byte[32];
        rnd.nextBytes(randomBytes);
        VrfProof proof = vrfSecretKey.prove(randomBytes);
        return proof.proofToVRFHash(vrfSecretKey.publicImage(), randomBytes);
    }
}
