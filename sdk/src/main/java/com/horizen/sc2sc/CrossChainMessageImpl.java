package com.horizen.sc2sc;

import com.horizen.proposition.Proposition;

import java.util.Arrays;

public class CrossChainMessageImpl implements CrossChainMessage{

    private int messageType;
    private byte[] senderSidechain;
    private Proposition sender;
    private byte[] receiverSidechain;
    private Proposition receiver;
    private byte[] payload;

    public CrossChainMessageImpl(int msgType, byte[] senderSidechain, Proposition sender, byte[] receiverSidechain, Proposition receiver, byte[] payload) {
        this.messageType = msgType;
        this.senderSidechain = senderSidechain;
        this.sender = sender;
        this.receiverSidechain = receiverSidechain;
        this.receiver = receiver;
        this.payload = payload;
    }

    @Override
    public CrossChainProtocolVersion getProtocolVersion() {
        return CrossChainProtocolVersion.VERSION_1;
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
    public Proposition getSender() {
        return sender;
    }

    public void setSender(Proposition sender) {
        this.sender = sender;
    }

    @Override
    public byte[] getReceiverSidechain() {
        return receiverSidechain;
    }

    public void setReceiverSidechain(byte[] receiverSidechain) {
        this.receiverSidechain = receiverSidechain;
    }

    @Override
    public Proposition getReceiver() {
        return receiver;
    }

    public void setReceiver(Proposition receiver) {
        this.receiver = receiver;
    }

    @Override
    public byte[] getPayload() {
        return payload;
    }

    public void setPayload(byte[] payload) {
        this.payload = payload;
    }

    @Override
    public String toString() {
        return "CrossChainMessage{" +
                "messageType=" + messageType +
                ", senderSidechain=" + Arrays.toString(senderSidechain) +
                ", sender=" + sender +
                ", receiverSidechain=" + Arrays.toString(receiverSidechain) +
                ", receiver=" + receiver +
                ", payload=" + Arrays.toString(payload) +
                '}';
    }
}
