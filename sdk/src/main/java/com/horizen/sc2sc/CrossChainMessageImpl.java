package com.horizen.sc2sc;
import com.fasterxml.jackson.annotation.JsonView;
import com.horizen.serialization.Views;
import com.horizen.utils.BytesUtils;

@JsonView(Views.Default.class)
public class CrossChainMessageImpl implements CrossChainMessage{

    private CrossChainProtocolVersion version;
    private int messageType;
    private byte[] senderSidechain;
    private byte[] sender;
    private byte[] receiverSidechain;
    private byte[] receiver;
    private byte[] payload;

    public CrossChainMessageImpl(CrossChainProtocolVersion version, int msgType, byte[] senderSidechain, byte[]  sender, byte[] receiverSidechain, byte[]  receiver, byte[] payload) {
        this.version = version;
        this.messageType = msgType;
        this.senderSidechain = senderSidechain;
        this.sender = sender;
        this.receiverSidechain = receiverSidechain;
        this.receiver = receiver;
        this.payload = payload;
    }

    @Override
    public CrossChainProtocolVersion getProtocolVersion() {
        return this.version;
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
    public byte[]  getSender() {
        return sender;
    }

    public void setSender(byte[]  sender) {
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
    public byte[]  getReceiver() {
        return receiver;
    }

    public void setReceiver(byte[]  receiver) {
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
    public byte[] bytes() {
        return serializer().toBytes(this);
    }

    @Override
    public CrossChainMessageSerializer serializer() {
        return CrossChainMessageSerializer.getSerializer();
    }

    @Override
    public String toString() {
        return "CrossChainMessage{" +
                "messageType=" + messageType +
                ", senderSidechain=" + BytesUtils.toHexString(senderSidechain) +
                ", sender=" + BytesUtils.toHexString(sender) +
                ", receiverSidechain=" + BytesUtils.toHexString(receiverSidechain) +
                ", receiver=" + BytesUtils.toHexString(receiver) +
                '}';
    }
}
