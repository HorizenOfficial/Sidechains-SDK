package com.horizen.cryptolibprovider;

import com.horizen.block.WithdrawalEpochCertificate;
import com.horizen.box.WithdrawalRequestBox;
import com.horizen.certnative.BackwardTransfer;
import com.horizen.certnative.WithdrawalCertificate;
import com.horizen.librustsidechains.FieldElement;
import com.horizen.provingsystemnative.ProvingSystem;
import com.horizen.provingsystemnative.ProvingSystemType;
import com.horizen.utils.BytesUtils;
import scala.Enumeration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.stream.Collectors;

public class  CommonCircuit {
    private static final int maxSegmentSize = (1 << 18);

    // Keys total max size values are the same as in MC
    public static final int maxProofPlusVkSize = 9 * 1024;

    // 2 custom fields that represent UtxoMerkleTreeRoot
    public static final int CUSTOM_FIELDS_NUMBER_WITH_ENABLED_CSW = 2;

    public static final int CUSTOM_FIELDS_NUMBER_WITH_DISABLED_CSW = 0;

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

    public static BackwardTransfer withdrawalRequestBoxToBackwardTransfer(WithdrawalRequestBox box) {
        return new BackwardTransfer(box.proposition().bytes(), box.value());
    }

    public static WithdrawalCertificate createWithdrawalCertificate(WithdrawalEpochCertificate cert, Enumeration.Value sidechainCreationVersion) {
        return new WithdrawalCertificate(
                FieldElement.deserialize(cert.sidechainId()),
                cert.epochNumber(),
                scala.collection.JavaConverters.seqAsJavaList(cert.backwardTransferOutputs()).stream().map(bto -> new BackwardTransfer(bto.pubKeyHash(), bto.amount())).collect(Collectors.toList()),
                cert.quality(),
                FieldElement.deserialize(cert.endCumulativeScTxCommitmentTreeRoot()),
                cert.ftMinAmount(),
                cert.btrFee(),
                Arrays.stream(cert.customFieldsOpt(sidechainCreationVersion).get()).map(FieldElement::deserialize).collect(Collectors.toList())
        );
    }
}
