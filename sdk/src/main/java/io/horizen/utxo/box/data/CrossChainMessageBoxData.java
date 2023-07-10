package io.horizen.utxo.box.data;

import com.google.common.primitives.Bytes;
import com.google.common.primitives.Shorts;
import io.horizen.proposition.PublicKey25519Proposition;
import io.horizen.sc2sc.CrossChainProtocolVersion;
import io.horizen.utils.BytesUtils;
import io.horizen.utxo.box.CrossChainMessageBox;
import sparkz.crypto.hash.Blake2b256;

import java.util.Objects;

public final class CrossChainMessageBoxData extends AbstractBoxData<PublicKey25519Proposition, CrossChainMessageBox, CrossChainMessageBoxData> {

    private final CrossChainProtocolVersion protocolVersion;
    private final int messageType;
    private final byte[]  receiverSidechain;
    private final byte[]  receiverAddress;
    private final byte[] payload;

    public CrossChainMessageBoxData(PublicKey25519Proposition proposition,
                                    CrossChainProtocolVersion protocolVersion,
                                    int messageType,
                                    byte[] receiverSidechain,
                                    byte[] receiverAddress,
                                    byte[] payload) {
        super(proposition, 0);
        Objects.requireNonNull(protocolVersion, "protocol version must be defined");
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

    public int getMessageType() {
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
        return Blake2b256.hash(Bytes.concat(Shorts.toByteArray(protocolVersion.getVal()),
                new byte[]{(byte)messageType}, receiverSidechain, receiverAddress, payload));
    }

    @Override
    public String toString() {
        return "CrossChainMessageBoxData{" +
                "protocolVersion=" + protocolVersion +
                ", messageType=" + messageType +
                ", receiverSidechain=" + BytesUtils.toHexString(receiverSidechain) +
                ", receiverAddress=" + BytesUtils.toHexString(receiverAddress) +
                ", payload=" + BytesUtils.toHexString(payload) +
                '}';
    }
}

