package io.horizen.utxo.box.data;

import io.horizen.proposition.PublicKey25519Proposition;
import io.horizen.proposition.PublicKey25519PropositionSerializer;
import io.horizen.sc2sc.CrossChainProtocolVersion;
import sparkz.util.serialization.Reader;
import sparkz.util.serialization.Writer;


public final class CrossChainMessageBoxDataSerializer implements BoxDataSerializer<CrossChainMessageBoxData> {

    private final static CrossChainMessageBoxDataSerializer serializer = new CrossChainMessageBoxDataSerializer();

    private CrossChainMessageBoxDataSerializer() {
        super();
    }

    public static CrossChainMessageBoxDataSerializer getSerializer() {
        return serializer;
    }

    @Override
    public void serialize(CrossChainMessageBoxData boxData, Writer writer) {
        boxData.proposition().serializer().serialize(boxData.proposition(), writer);
        writer.putShort(boxData.getProtocolVersion().getVal());
        writer.putInt(boxData.getMessageType());
        writer.putInt(boxData.getReceiverSidechain().length);
        writer.putBytes(boxData.getReceiverSidechain());
        writer.putInt(boxData.getReceiverAddress().length);
        writer.putBytes(boxData.getReceiverAddress());
        writer.putInt(boxData.getPayload().length);
        writer.putBytes(boxData.getPayload());
    }

    @Override
    public CrossChainMessageBoxData parse(Reader reader) {
        PublicKey25519Proposition proposition = PublicKey25519PropositionSerializer.getSerializer().parse(reader);
        CrossChainProtocolVersion protocolVersion = CrossChainProtocolVersion.fromShort(reader.getShort());
        Integer messageType = reader.getInt();
        byte[]  receiverSidechain = reader.getBytes(reader.getInt());
        byte[]  receiverAddress = reader.getBytes(reader.getInt());
        byte[]  payload = reader.getBytes(reader.getInt());
        return new CrossChainMessageBoxData(proposition, protocolVersion, messageType, receiverSidechain, receiverAddress, payload);
    }
}