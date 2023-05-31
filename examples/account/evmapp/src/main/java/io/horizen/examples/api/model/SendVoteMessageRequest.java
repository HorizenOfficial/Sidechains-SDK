package io.horizen.examples.api.model;

public final class SendVoteMessageRequest {
    private int messageType;
    private String sender;
    private String receiverSidechain;
    private String receiver;
    private int payload;

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
}
