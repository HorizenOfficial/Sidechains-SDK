package io.horizen.sc2sc;

import com.fasterxml.jackson.annotation.JsonView;
import com.horizen.librustsidechains.FieldElement;
import io.horizen.cryptolibprovider.utils.FieldElementUtils;
import io.horizen.cryptolibprovider.utils.HashUtils;
import io.horizen.json.Views;
import io.horizen.utils.BytesUtils;
import io.horizen.utils.FieldElementsContainer;
import sparkz.core.serialization.BytesSerializable;

@JsonView(Views.Default.class)
public final class CrossChainMessage implements BytesSerializable {
    private final static CrossChainMessageSemanticValidator ccMsgValidator = new CrossChainMessageSemanticValidator();
    private final CrossChainProtocolVersion version;
    private final int messageType;
    private final byte[] senderSidechain;
    private final byte[] sender;
    private final byte[] receiverSidechain;
    private final byte[] receiver;
    private final byte[] payloadHash;

    public CrossChainMessage(CrossChainProtocolVersion version, int msgType, byte[] senderSidechain, byte[]  sender, byte[] receiverSidechain, byte[]  receiver, byte[] payloadHash) {
        this.version = version;
        this.messageType = msgType;
        this.senderSidechain = senderSidechain;
        this.sender = sender;
        this.receiverSidechain = receiverSidechain;
        this.receiver = receiver;
        this.payloadHash = payloadHash;

        ccMsgValidator.validateMessage(this);
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

    public byte[] getPayloadHash() {
        return payloadHash;
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
}
