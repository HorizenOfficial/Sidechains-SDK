package com.horizen.cryptolibprovider;

import com.horizen.provingsystemnative.ProvingSystem;
import com.horizen.provingsystemnative.ProvingSystemType;
import com.horizen.utils.BytesUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

public class CommonCircuit {
    private static final int maxSegmentSize = (1 << 18);

    // Keys total max size values are the same as in MC
    public static final int maxProofPlusVkSize = 9 * 1024;

    // 2 custom fields that represent UtxoMerkleTreeRoot
    public static final int customFieldsNumber = 2;

    public boolean generateCoboundaryMarlinDLogKeys() {
        return ProvingSystem.generateDLogKeys(
                ProvingSystemType.COBOUNDARY_MARLIN,
                maxSegmentSize);
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
