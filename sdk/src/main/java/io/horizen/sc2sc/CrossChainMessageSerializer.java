package io.horizen.sc2sc;

import sparkz.util.serialization.Reader;
import sparkz.util.serialization.Writer;
import sparkz.core.serialization.SparkzSerializer;

public class CrossChainMessageSerializer<T extends CrossChainMessage> implements SparkzSerializer<T> {

    private static CrossChainMessageSerializer serializer;

    static {
        serializer = new CrossChainMessageSerializer<CrossChainMessageImpl>();
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
        w.putInt(s.getPayload().length);
        w.putBytes(s.getPayload());
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
        return (T)new CrossChainMessageImpl(
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

