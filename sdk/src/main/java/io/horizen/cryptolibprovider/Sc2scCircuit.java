package io.horizen.cryptolibprovider;

import com.horizen.certnative.WithdrawalCertificate;
import com.horizen.commitmenttreenative.ScCommitmentCertPath;
import com.horizen.librustsidechains.FieldElement;
import com.horizen.merkletreenative.InMemoryAppendOnlyMerkleTree;
import com.horizen.merkletreenative.MerklePath;
import io.horizen.sc2sc.CrossChainMessage;
import io.horizen.sc2sc.CrossChainMessageHash;

import java.util.List;

public interface Sc2scCircuit {
    boolean generateSc2ScKeys(
            String provingKeyPath,
            String verificationKeyPath
    ) throws Exception;

    int getMaxCrossChainMessagesPerEpoch();

    CrossChainMessageHash getCrossChainMessageHash(CrossChainMessage msg) throws Exception;

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