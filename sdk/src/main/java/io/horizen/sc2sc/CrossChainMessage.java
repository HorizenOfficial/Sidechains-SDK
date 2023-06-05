package io.horizen.sc2sc;
import com.fasterxml.jackson.annotation.JsonView;
import com.horizen.librustsidechains.FieldElement;
import io.horizen.cryptolibprovider.utils.FieldElementUtils;
import io.horizen.cryptolibprovider.utils.HashUtils;
import io.horizen.json.Views;
import io.horizen.utils.BytesUtils;
import io.horizen.utils.Constants;
import io.horizen.utils.FieldElementsContainer;
import sparkz.core.serialization.BytesSerializable;

import java.util.Arrays;
import java.util.Objects;

@JsonView(Views.Default.class)
public final class CrossChainMessage implements BytesSerializable {
    private final CrossChainProtocolVersion version;
    private final int messageType;
    private final byte[] senderSidechain;
    private final byte[] sender;
    private final byte[] receiverSidechain;
    private final byte[] receiver;
    private final byte[] payload;

    public CrossChainMessage(CrossChainProtocolVersion version, int msgType, byte[] senderSidechain, byte[]  sender, byte[] receiverSidechain, byte[]  receiver, byte[] payload) {
        validateArguments(msgType, senderSidechain, receiverSidechain, sender, receiver);
        this.version = version;
        this.messageType = msgType;
        this.senderSidechain = senderSidechain;
        this.sender = sender;
        this.receiverSidechain = receiverSidechain;
        this.receiver = receiver;
        this.payload = payload;
    }

    private void validateArguments(int msgType, byte[] senderSidechain, byte[] receiverSidechain, byte[] sender, byte[] receiver) {
        if (msgType < 0) {
            throw new IllegalArgumentException("CrossChain message type cannot be negative");
        }

        if (senderSidechain.length != Constants.SIDECHAIN_ID_SIZE()) {
            throw new IllegalArgumentException("Sender sidechain id must be 32 bytes long");
        }

        if (receiverSidechain.length != Constants.SIDECHAIN_ID_SIZE()) {
            throw new IllegalArgumentException("Receiver sidechain id must be 32 bytes long");
        }

        if (sender.length == 0 || sender.length > Constants.SIDECHAIN_ADDRESS_SIZE()) {
            throw new IllegalArgumentException("Sender address length is not correct");
        }

        if (receiver.length == 0 || receiver.length > Constants.SIDECHAIN_ADDRESS_SIZE()) {
            throw new IllegalArgumentException("Receiver address length is not correct");
        }
    }

    public CrossChainProtocolVersion getProtocolVersion() {
        return this.version;
    }

    public int getMessageType() {
        return messageType;
    }

    public byte[] getSenderSidechain() {
        return senderSidechain;
    }

    public byte[]  getSender() {
        return sender;
    }

    public byte[] getReceiverSidechain() {
        return receiverSidechain;
    }

    public byte[] getReceiver() {
        return receiver;
    }

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

    public CrossChainMessageHash getCrossChainMessageHash() throws Exception {
        try (
                FieldElementsContainer fieldElementsContainer = FieldElementUtils.deserializeMany(bytes());
                FieldElement fe = HashUtils.fieldElementsListHash(fieldElementsContainer.getFieldElementCollection())
        ) {
            return new CrossChainMessageHash(fe.serializeFieldElement());
        }
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CrossChainMessage that = (CrossChainMessage) o;
        return messageType == that.messageType && version == that.version && Arrays.equals(senderSidechain, that.senderSidechain)
                && Arrays.equals(sender, that.sender) && Arrays.equals(receiverSidechain, that.receiverSidechain)
                && Arrays.equals(receiver, that.receiver) && Arrays.equals(payload, that.payload);
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
}
