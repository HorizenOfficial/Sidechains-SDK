package com.horizen.cryptolibprovider;

import com.horizen.provingsystemnative.ProvingSystem;
import com.horizen.provingsystemnative.ProvingSystemType;
import com.horizen.utils.BytesUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

public class CommonCircuit {
    // Note: supportedSegmentSize should correlate with the snark circuit complexity, but is always less or equal the one defined in the MC network (maxSegmentSize).
    private static final int maxSegmentSize = (1 << 18);
    private static final int supportedSegmentSize = (1 << 17);

    public boolean generateCoboundaryMarlinDLogKeys() {
        return ProvingSystem.generateDLogKeys(
                ProvingSystemType.COBOUNDARY_MARLIN,
                maxSegmentSize,
                supportedSegmentSize);
    }

    public String getCoboundaryMarlinSnarkVerificationKeyHex(String verificationKeyPath) {
        if(!Files.exists(Paths.get(verificationKeyPath)))
            return "";

        try {
            return BytesUtils.toHexString(Files.readAllBytes(Paths.get(verificationKeyPath)));
        } catch (IOException e) {
            return "";
        }
    }
}
