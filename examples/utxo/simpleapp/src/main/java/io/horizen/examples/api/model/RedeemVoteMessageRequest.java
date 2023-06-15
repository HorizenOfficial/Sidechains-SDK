package io.horizen.examples.api.model;

public final class RedeemVoteMessageRequest {
    private String proposition;
    private String certificateDataHash;
    private String nextCertificateDataHash;
    private String scCommitmentTreeRoot;
    private String nextScCommitmentTreeRoot;
    private String proof;
    private int messageType;
    private String senderSidechain;
    private String sender;
    private String receiverSidechain;
    private String receiver;
    private String payload;
    private long fee;

    public String getProposition() {
        return proposition;
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

    public String getProof() {
        return proof;
    }

    public int getMessageType() {
        return messageType;
    }

    public String getSenderSidechain() {
        return senderSidechain;
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

    public long getFee() {
        return fee;
    }
}