package io.horizen.sc2sc;

import com.fasterxml.jackson.annotation.JsonView;
import io.horizen.json.Views;
import io.horizen.utils.BytesUtils;

@JsonView(Views.Default.class)
public class CrossChainRedeemMessageImpl implements CrossChainRedeemMessage {

    private CrossChainMessage message;
    private byte[] certificateDataHash;
    private byte[] nextCertificateDataHash;
    private byte[] scCommitmentTreeRoot;
    private byte[] nextScCommitmentTreeRoot;
    private byte[] proof;

    public CrossChainRedeemMessageImpl(
            CrossChainMessage message,
            byte[] certificateDataHash,
            byte[] nextCertificateDataHash,
            byte[] scCommitmentTreeRoot,
            byte[] nextScCommitmentTreeRoot,
            byte[] proof
    ) {
        this.message = message;
        this.certificateDataHash = certificateDataHash;
        this.nextCertificateDataHash = nextCertificateDataHash;
        this.scCommitmentTreeRoot = scCommitmentTreeRoot;
        this.nextScCommitmentTreeRoot = nextScCommitmentTreeRoot;
        this.proof = proof;
    }

    @Override
    public CrossChainMessage getMessage() {
        return message;
    }

    public void setMessage(CrossChainMessage message) {
        this.message = message;
    }

    @Override
    public byte[] getCertificateDataHash() {
        return certificateDataHash;
    }

    public void setCertificateDataHash(byte[] certificateDataHash) {
        this.certificateDataHash = certificateDataHash;
    }

    @Override
    public byte[] getNextCertificateDataHash() {
        return nextCertificateDataHash;
    }

    public void setNextCertificateDataHash(byte[] nextCertificateDataHash) {
        this.nextCertificateDataHash = nextCertificateDataHash;
    }

    @Override
    public byte[] getScCommitmentTreeRoot() {
        return scCommitmentTreeRoot;
    }

    public void setScCommitmentTreeRoot(byte[] scCommitmentTreeRoot) {
        this.scCommitmentTreeRoot = scCommitmentTreeRoot;
    }

    @Override
    public byte[] getNextScCommitmentTreeRoot() {
        return nextScCommitmentTreeRoot;
    }

    public void setNextScCommitmentTreeRoot(byte[] nextScCommitmentTreeRoot) {
        this.nextScCommitmentTreeRoot = nextScCommitmentTreeRoot;
    }

    @Override
    public byte[] getProof() {
        return proof;
    }

    public void setProof(byte[] proof) {
        this.proof = proof;
    }

    @Override
    public String toString() {
        return "CrossChainRedeemMessage{" +
                "message=" + message.toString() +
                ", certificateDataHash=" + BytesUtils.toHexString(certificateDataHash) +
                ", nextCertificateDataHash=" + BytesUtils.toHexString(nextCertificateDataHash) +
                ", scCommitmentTreeRoot=" + BytesUtils.toHexString(scCommitmentTreeRoot) +
                ", nextScCommitmentTreeRoot=" + BytesUtils.toHexString(nextScCommitmentTreeRoot) +
                '}';
    }
}
