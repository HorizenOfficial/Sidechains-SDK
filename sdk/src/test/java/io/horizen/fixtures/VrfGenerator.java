package io.horizen.fixtures;

import io.horizen.secret.VrfKeyGenerator;
import io.horizen.proof.VrfProof;
import io.horizen.secret.VrfSecretKey;
import io.horizen.utils.Pair;
import io.horizen.vrf.VrfOutput;

import java.util.Random;

public class VrfGenerator {

    private static byte[] generateRandomMessage(long seed) {
        Random rnd = new Random(seed);
        byte[] randomBytes = new byte[32];
        rnd.nextBytes(randomBytes);
        randomBytes[31] = 0; // nullify last byte to fit Tweedle 254bit FE.
        return randomBytes;
    }

    public static Pair<VrfProof, VrfOutput> generateProofAndOutput(long seed) {
        Random rnd = new Random(seed);
        byte[] vrfSeed = new byte[32];
        rnd.nextBytes(vrfSeed);
        VrfSecretKey vrfSecretKey = VrfKeyGenerator.getInstance().generateSecret(vrfSeed);
        byte[] message = generateRandomMessage(seed);
        VrfProof proof = vrfSecretKey.prove(message).getKey();
        VrfOutput output = proof.proofToVrfOutput(vrfSecretKey.publicImage(), message).get();

        return new Pair<>(proof, output);
    }

    public static VrfProof generateProof(long seed) {
        return generateProofAndOutput(seed).getKey();
    }

    public static VrfOutput generateVrfOutput(long seed) {
        Random rnd = new Random(seed);
        byte[] vrfSeed = new byte[32];
        rnd.nextBytes(vrfSeed);
        VrfSecretKey vrfSecretKey = VrfKeyGenerator.getInstance().generateSecret(vrfSeed);
        byte[] randomBytes = generateRandomMessage(seed);
        VrfProof proof = vrfSecretKey.prove(randomBytes).getKey();
        return proof.proofToVrfOutput(vrfSecretKey.publicImage(), randomBytes).get();
    }
}
