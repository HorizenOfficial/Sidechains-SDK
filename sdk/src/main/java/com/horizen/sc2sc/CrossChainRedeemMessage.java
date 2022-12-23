package com.horizen.sc2sc;

public interface CrossChainRedeemMessage {

    CrossChainMessage getMessage();
    byte[] getCertificateDataHash();
    byte[] getNextCertificateDataHash();
    byte[] getScCommitmentTreeRoot();
    byte[] getNextScCommitmentTreeRoot();
    byte[] getProof();
}
