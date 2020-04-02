package com.horizen.fixtures;

import com.horizen.vrf.VRFKeyGenerator;
import com.horizen.vrf.VRFProof;
import com.horizen.vrf.VRFPublicKey;
import com.horizen.vrf.VRFSecretKey;
import scala.Tuple2;

import java.util.Random;

public class VrfGenerator {
    public static VRFProof generateProof(long seed) {
        Random rnd = new Random(seed);
        byte[] vrfSeed = new byte[32];
        rnd.nextBytes(vrfSeed);
        Tuple2<VRFSecretKey, VRFPublicKey> vrfKeys = VRFKeyGenerator.generate(vrfSeed);

        byte[] vrfMessage = new byte[32];
        rnd.nextBytes(vrfMessage);

        return vrfKeys._1.prove(vrfMessage);
    }
}
