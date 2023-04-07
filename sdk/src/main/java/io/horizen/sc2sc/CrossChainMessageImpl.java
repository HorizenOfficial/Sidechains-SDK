package io.horizen.sc2sc;

import com.fasterxml.jackson.annotation.JsonView;
import io.horizen.json.Views;
import io.horizen.utils.BytesUtils;

import java.util.Arrays;
import java.util.Objects;

@JsonView(Views.Default.class)
public class CrossChainMessageImpl implements CrossChainMessage {
    private final CrossChainProtocolVersion version;
    private final int messageType;
    private final byte[] senderSidechain;
    private final byte[] sender;
    private final byte[] receiverSidechain;
    private final byte[] receiver;
    private final byte[] payload;

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

    @Override
    public byte[] getSenderSidechain() {
        return senderSidechain;
    }

    @Override
    public byte[]  getSender() {
        return sender;
    }

    @Override
    public byte[] getReceiverSidechain() {
        return receiverSidechain;
    }

    @Override
    public byte[] getReceiver() {
        return receiver;
    }

    @Override
    public byte[] getPayload() {
        return payload;
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
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CrossChainMessageImpl that = (CrossChainMessageImpl) o;
        return messageType == that.messageType
                && version == that.version
                && Arrays.equals(senderSidechain, that.senderSidechain)
                && Arrays.equals(sender, that.sender)
                && Arrays.equals(receiverSidechain, that.receiverSidechain)
                && Arrays.equals(receiver, that.receiver)
                && Arrays.equals(payload, that.payload);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(version, messageType);
        result = 31 * result + Arrays.hashCode(senderSidechain);
        result = 31 * result + Arrays.hashCode(sender);
        result = 31 * result + Arrays.hashCode(receiverSidechain);
        result = 31 * result + Arrays.hashCode(receiver);
        result = 31 * result + Arrays.hashCode(payload);
        return result;
    }

    @Override
    public String toString() {
        return "CrossChainMessageImpl{" +
                "version=" + version +
                ", messageType=" + messageType +
                ", senderSidechain=" + BytesUtils.toHexString(senderSidechain) +
                ", sender=" + BytesUtils.toHexString(sender) +
                ", receiverSidechain=" + BytesUtils.toHexString(receiverSidechain) +
                ", receiver=" + BytesUtils.toHexString(receiver) +
                ", payload=" + BytesUtils.toHexString(payload) +
                '}';
    }
}
