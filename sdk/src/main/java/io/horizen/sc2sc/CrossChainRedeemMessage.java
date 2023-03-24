package io.horizen.sc2sc;

import io.horizen.sc2sc.CrossChainMessage;

public interface CrossChainRedeemMessage {

    CrossChainMessage getMessage();
    byte[] getCertificateDataHash();
    byte[] getNextCertificateDataHash();
    byte[] getScCommitmentTreeRoot();
    byte[] getNextScCommitmentTreeRoot();
    byte[] getProof();
}
