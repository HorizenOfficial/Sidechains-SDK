package com.horizen.cryptolibprovider;


import com.horizen.certnative.WithdrawalCertificate;
import com.horizen.librustsidechains.FieldElement;
import com.horizen.merkletreenative.MerklePath;
import com.horizen.sc2sc.CrossChainMessage;
import com.horizen.sc2sc.CrossChainMessageHash;

import java.util.List;

public interface Sc2scCircuit {

    int getMaxMessagesPerCertificate();

    CrossChainMessageHash getCrossChainMessageHash(CrossChainMessage msg);

    byte[] getCrossChainMessageTreeRoot(List<CrossChainMessage> messages);

    MerklePath gerCrossChainMessageMerklePath(List<CrossChainMessage> messages, CrossChainMessage leaf);

    byte[] createRedeemProof(CrossChainMessageHash messageHash,
                             MerklePath messageMerkePath,
                             WithdrawalCertificate topQuality_cert_epochN,
                             MerklePath merklePath_topQuality_cert_epochN,
                             FieldElement sc_tx_commitment_root_cert_epochN,
                             WithdrawalCertificate cert_epoch_N1,
                             MerklePath merklePath_cert_epochN1,
                             FieldElement sc_tx_commitment_root_cert_epochN1
                             );

    boolean verifyRedeemProof(CrossChainMessageHash messageHash,
                              FieldElement sc_tx_commitment_root_cert_epochN,
                              MerklePath merklePath_topQuality_cert_epochN1,
                              byte[] proof
    );

}
