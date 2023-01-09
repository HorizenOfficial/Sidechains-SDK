package com.horizen.sc2sc;

public class CrossChainMessageImpl implements CrossChainMessage{

    private int messageType;
    private byte[] senderSidechain;
    private byte[] senderAddress;
    private byte[] receiverSidechain;
    private byte[] receiverAddress;
    private byte[] payload;

    public CrossChainMessageImpl(int msgType, byte[] senderSidechain, byte[] sender, byte[] receiverSidechain, byte[] receiverAddress, byte[] payload) {
        this.messageType = msgType;
        this.senderSidechain = senderSidechain;
        this.receiverSidechain = receiverSidechain;
        this.receiverAddress = receiverAddress;
        this.payload = payload;
    }

    @Override
    public int getMessageType() {
        return messageType;
    }

    public void setMessageType(int messageType) {
        this.messageType = messageType;
    }

    @Override
    public byte[] getSenderSidechain() {
        return senderSidechain;
    }

    public void setSenderSidechain(byte[] senderSidechain) {
        this.senderSidechain = senderSidechain;
    }

    @Override
    public byte[] getSenderAddress() {
        return senderAddress;
    }

    public void setSenderAddress(byte[] senderAddress) {
        this.senderAddress = senderAddress;
    }

    @Override
    public byte[] getReceiverSidechain() {
        return receiverSidechain;
    }

    public void setReceiverSidechain(byte[] receiverSidechain) {
        this.receiverSidechain = receiverSidechain;
    }

    @Override
    public byte[] getReceiverAddress() {
        return receiverAddress;
    }

    public void setReceiverAddress(byte[] receiverAddress) {
        this.receiverAddress = receiverAddress;
    }

    @Override
    public byte[] getPayload() {
        return payload;
    }

    public void setPayload(byte[] payload) {
        this.payload = payload;
    }

}
