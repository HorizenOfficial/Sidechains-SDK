package io.horizen.examples.api.model;

public final class RedeemVoteMessageRequest {
    private int messageType;
    private String sender;
    private String receiverSidechain;
    private String receiver;
    private int payload;
    private String certificateDataHash;
    private String nextCertificateDataHash;
    private String scCommitmentTreeRoot;
    private String nextScCommitmentTreeRoot;
    private String proof;


    public String getProof() {
        return proof;
    }

    public void setProof(String proof) {
        this.proof = proof;
    }


    public int getMessageType() {
        return messageType;
    }

    public void setMessageType(int messageType) {
        this.messageType = messageType;
    }

    public String getSender() {
        return sender;
    }

    public void setSender(String sender) {
        this.sender = sender;
    }

    public String getReceiverSidechain() {
        return receiverSidechain;
    }

    public void setReceiverSidechain(String receiverSidechain) {
        this.receiverSidechain = receiverSidechain;
    }

    public String getReceiver() {
        return receiver;
    }

    public void setReceiver(String receiver) {
        this.receiver = receiver;
    }

    public int getPayload() {
        return payload;
    }

    public void setPayload(int payload) {
        this.payload = payload;
    }

    public String getCertificateDataHash() {
        return certificateDataHash;
    }

    public void setCertificateDataHash(String certificateDataHash) {
        this.certificateDataHash = certificateDataHash;
    }

    public String getNextCertificateDataHash() {
        return nextCertificateDataHash;
    }

    public void setNextCertificateDataHash(String nextCertificateDataHash) {
        this.nextCertificateDataHash = nextCertificateDataHash;
    }

    public String getScCommitmentTreeRoot() {
        return scCommitmentTreeRoot;
    }

    public void setScCommitmentTreeRoot(String scCommitmentTreeRoot) {
        this.scCommitmentTreeRoot = scCommitmentTreeRoot;
    }

    public String getNextScCommitmentTreeRoot() {
        return nextScCommitmentTreeRoot;
    }

    public void setNextScCommitmentTreeRoot(String nextScCommitmentTreeRoot) {
        this.nextScCommitmentTreeRoot = nextScCommitmentTreeRoot;
    }
}
