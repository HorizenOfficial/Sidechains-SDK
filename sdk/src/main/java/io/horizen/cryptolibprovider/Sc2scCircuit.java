package io.horizen.cryptolibprovider;

import com.horizen.certnative.WithdrawalCertificate;
import com.horizen.commitmenttreenative.ScCommitmentCertPath;
import com.horizen.merkletreenative.MerklePath;
import io.horizen.sc2sc.CrossChainMessageHash;

public interface Sc2scCircuit {
    boolean generateSc2ScKeys(
            String provingKeyPath,
            String verificationKeyPath
    ) throws Exception;

    int getMaxCrossChainMessagesPerEpoch();

    byte[] createRedeemProof(CrossChainMessageHash messageHash,
                             byte[] scTxCommitmentRoot,
                             byte[] nextScTxCommitmentRoot,
                             WithdrawalCertificate currWithdrawalCertificate,
                             WithdrawalCertificate nextWithdrawalCertificate,
                             ScCommitmentCertPath merklePathTopQualityCert,
                             ScCommitmentCertPath nextMerklePathTopQualityCert,
                             MerklePath messageMerklePath,
                             String provingKeyPath
    );

    boolean verifyRedeemProof(CrossChainMessageHash messageHash,
                              byte[] sc_tx_commitment_root_cert_epochN,
                              byte[] sc_tx_commitment_root_cert_epochN1,
                              byte[] proof,
                              String verifyKeyPath
    );
}