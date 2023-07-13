package io.horizen.examples.api.model;

public final class RedeemVoteMessageRequest {
    private int messageType;
    private String sender;
    private String receiverSidechain;
    private String receiver;
    private String payload;
    private String certificateDataHash;
    private String nextCertificateDataHash;
    private String scCommitmentTreeRoot;
    private String nextScCommitmentTreeRoot;
    private String proof;

    public String getProof() {
        return proof;
    }

    public int getMessageType() {
        return messageType;
    }

    public String getSender() {
        return sender;
    }

    public String getReceiverSidechain() {
        return receiverSidechain;
    }

    public String getReceiver() {
        return receiver;
    }

    public String getPayload() {
        return payload;
    }

    public String getCertificateDataHash() {
        return certificateDataHash;
    }

    public String getNextCertificateDataHash() {
        return nextCertificateDataHash;
    }

    public String getScCommitmentTreeRoot() {
        return scCommitmentTreeRoot;
    }

    public String getNextScCommitmentTreeRoot() {
        return nextScCommitmentTreeRoot;
    }
}
