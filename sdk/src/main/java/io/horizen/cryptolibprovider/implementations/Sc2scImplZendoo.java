package io.horizen.cryptolibprovider.implementations;

import com.horizen.certnative.WithdrawalCertificate;
import com.horizen.commitmenttreenative.ScCommitmentCertPath;
import com.horizen.librustsidechains.Constants;
import com.horizen.librustsidechains.FieldElement;
import com.horizen.merkletreenative.MerklePath;
import com.horizen.provingsystemnative.ProvingSystemType;
import com.horizen.sc2scnative.Sc2Sc;
import io.horizen.cryptolibprovider.CommonCircuit;
import io.horizen.cryptolibprovider.Sc2scCircuit;
import io.horizen.cryptolibprovider.utils.FieldElementUtils;
import io.horizen.cryptolibprovider.utils.HashUtils;
import io.horizen.sc2sc.CrossChainMessage;
import io.horizen.sc2sc.CrossChainMessageHash;
import io.horizen.utils.FieldElementsContainer;

import java.util.Optional;

public class Sc2scImplZendoo implements Sc2scCircuit {

    private static final int SEGMENT_SIZE = 1 << 15;
    public static final int CUSTOM_FIELDS_NUM = 32;

    @Override
    public boolean generateSc2ScKeys(String provingKeyPath, String verificationKeyPath) throws Exception {
        return Sc2Sc.setup(
                ProvingSystemType.COBOUNDARY_MARLIN,
                CUSTOM_FIELDS_NUM,
                Optional.of(SEGMENT_SIZE),
                provingKeyPath,
                verificationKeyPath,
                true,
                CommonCircuit.maxProofPlusVkSize
        );
    }

    @Override
    public int getMaxCrossChainMessagesPerEpoch() {
        return 1 << Constants.MSG_MT_HEIGHT();
    }

    @Override
    public byte[] createRedeemProof(CrossChainMessageHash messageHash,
                                    byte[] scTxCommitmentRoot,
                                    byte[] nextScTxCommitmentRoot,
                                    WithdrawalCertificate currWithdrawalCertificate,
                                    WithdrawalCertificate nextWithdrawalCertificate,
                                    ScCommitmentCertPath merklePathTopQualityCert,
                                    ScCommitmentCertPath nextMerklePathTopQualityCert,
                                    MerklePath messageMerklePath,
                                    String provingKeyPath
    ) {
        return Sc2Sc.createProof(
                nextScTxCommitmentRoot,
                scTxCommitmentRoot,
                messageHash.getValue(),
                nextWithdrawalCertificate,
                currWithdrawalCertificate,
                nextMerklePathTopQualityCert,
                merklePathTopQualityCert,
                messageMerklePath,
                provingKeyPath,
                Optional.of(SEGMENT_SIZE),
                true
        );
    }

    @Override
    public boolean verifyRedeemProof(CrossChainMessageHash messageHash,
                                     byte[] scTxCommitmentRootCertEpoch,
                                     byte[] nextScTxCommitmentRootCertEpoch,
                                     byte[] proof,
                                     String verifyKeyPath) {
        return Sc2Sc.verifyProof(
                nextScTxCommitmentRootCertEpoch,
                scTxCommitmentRootCertEpoch,
                messageHash.getValue(),
                proof,
                verifyKeyPath
        );
    }

    @Override
    public CrossChainMessageHash getCrossChainMessageHash(CrossChainMessage msg) throws Exception {
        try (
                FieldElementsContainer fieldElementsContainer = FieldElementUtils.deserializeMany(msg.bytes());
                FieldElement fe = HashUtils.fieldElementListHash(fieldElementsContainer.getFieldElementCollection())
        ) {
            return new CrossChainMessageHash(fe.serializeFieldElement());
        }
    }
}
