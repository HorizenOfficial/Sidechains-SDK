package io.horizen.sc2sc;

import sparkz.core.serialization.SparkzSerializer;
import sparkz.util.serialization.Reader;
import sparkz.util.serialization.Writer;

public class CrossChainMessageSerializer<T extends CrossChainMessage> implements SparkzSerializer<T> {

    private static final CrossChainMessageSerializer serializer;

    static {
        serializer = new CrossChainMessageSerializer<>();
    }

    @Override
    public void serialize(T s, Writer w) {
        w.putShort(s.getProtocolVersion().getVal());
        w.putInt(s.getMessageType());
        w.putBytes(s.getSenderSidechain());
        w.putInt(s.getSender().length);
        w.putBytes(s.getSender());
        w.putBytes(s.getReceiverSidechain());
        w.putInt(s.getReceiver().length);
        w.putBytes(s.getReceiver());
        w.putInt(s.getPayloadHash().length);
        w.putBytes(s.getPayloadHash());
    }

    @Override
    public T parse(Reader reader) {
        short protocolVersion = reader.getShort();
        int messageType = reader.getInt();
        byte[] senderSidechain = reader.getBytes(32);
        byte[] sender = reader.getBytes(reader.getInt());
        byte[] receiverSidechain = reader.getBytes(32);
        byte[] receiver = reader.getBytes(reader.getInt());
        byte[] payload = reader.getBytes(reader.getInt());
        return (T)new CrossChainMessage(
            CrossChainProtocolVersion.fromShort(protocolVersion),
            messageType,
            senderSidechain,
            sender,
            receiverSidechain,
            receiver,
            payload
        );
    }

    public static CrossChainMessageSerializer<CrossChainMessage> getSerializer(){
        return serializer;
    }
}

