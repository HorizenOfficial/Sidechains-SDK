package io.horizen.sc2sc;
import com.fasterxml.jackson.annotation.JsonView;
import io.horizen.json.Views;
import io.horizen.utils.BytesUtils;

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
