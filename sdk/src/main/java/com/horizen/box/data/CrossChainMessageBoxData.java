package com.horizen.box.data;

import com.google.common.primitives.Bytes;
import com.horizen.box.CrossChainMessageBox;
import com.horizen.proposition.PublicKey25519Proposition;
import com.horizen.sc2sc.CrossChainProtocolVersion;
import scorex.crypto.hash.Blake2b256;

import java.util.Objects;

public final class CrossChainMessageBoxData extends AbstractBoxData<PublicKey25519Proposition, CrossChainMessageBox, CrossChainMessageBoxData> {

    private CrossChainProtocolVersion protocolVersion;
    private final Integer messageType;
    private final byte[]  receiverSidechain;
    private final byte[]  receiverAddress;
    private final byte[]  payload;

    public CrossChainMessageBoxData(PublicKey25519Proposition proposition,
                         CrossChainProtocolVersion protocolVersion,
                         Integer messageType,
                         byte[] receiverSidechain,
                         byte[] receiverAddress,
                         byte[] payload) {
        super(proposition, 0);
        Objects.requireNonNull(protocolVersion, "protocol version must be defined");
        Objects.requireNonNull(messageType, "messageType must be defined");
        Objects.requireNonNull(receiverSidechain, "receiverSidechain must be defined");
        Objects.requireNonNull(receiverAddress, "receiverAddress must be defined");
        Objects.requireNonNull(payload, "payload must be defined");
        this.protocolVersion = protocolVersion;
        this.messageType = messageType;
        this.receiverSidechain = receiverSidechain;
        this.receiverAddress = receiverAddress;
        this.payload = payload;
    }

    public CrossChainProtocolVersion getProtocolVersion() {
        return protocolVersion;
    }

    public Integer getMessageType() {
        return messageType;
    }

    public byte[] getReceiverSidechain() {
        return receiverSidechain;
    }

    public byte[] getReceiverAddress() {
        return receiverAddress;
    }

    public byte[] getPayload() {
        return payload;
    }

    @Override
    public CrossChainMessageBox getBox(long nonce) {
        return new CrossChainMessageBox(this, nonce);
    }

    @Override
    public BoxDataSerializer serializer() {
        return CrossChainMessageBoxDataSerializer.getSerializer();
    }

    @Override
    public byte[] customFieldsHash() {
        return Blake2b256.hash(Bytes.concat(new byte[]{messageType.byteValue()}, receiverSidechain, receiverAddress, payload));
    }
}

