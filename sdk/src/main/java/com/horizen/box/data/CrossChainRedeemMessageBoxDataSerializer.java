package com.horizen.box.data;

import com.horizen.proposition.PublicKey25519Proposition;
import com.horizen.proposition.PublicKey25519PropositionSerializer;
import com.horizen.sc2sc.CrossChainMessage;
import com.horizen.sc2sc.CrossChainMessageSerializer;
import sparkz.util.serialization.Reader;
import sparkz.util.serialization.Writer;

public class CrossChainRedeemMessageBoxDataSerializer implements BoxDataSerializer<CrossChainRedeemMessageBoxData> {

    private final static CrossChainRedeemMessageBoxDataSerializer serializer = new CrossChainRedeemMessageBoxDataSerializer();

    private CrossChainRedeemMessageBoxDataSerializer() {
        super();
    }

    public static CrossChainRedeemMessageBoxDataSerializer getSerializer() {
        return serializer;
    }

    @Override
    public void serialize(CrossChainRedeemMessageBoxData boxData, Writer writer) {
        PublicKey25519PropositionSerializer.getSerializer().serialize(boxData.proposition(), writer);
        boxData.getMessage().serializer().serialize(boxData.getMessage(), writer);

        writer.putInt(boxData.getCertificateDataHash().length);
        writer.putBytes(boxData.getCertificateDataHash());

        writer.putInt(boxData.getNextCertificateDataHash().length);
        writer.putBytes(boxData.getNextCertificateDataHash());

        writer.putInt(boxData.getScCommitmentTreeRoot().length);
        writer.putBytes(boxData.getScCommitmentTreeRoot());

        writer.putInt(boxData.getNextScCommitmentTreeRoot().length);
        writer.putBytes(boxData.getNextScCommitmentTreeRoot());

        writer.putInt(boxData.getProof().length);
        writer.putBytes(boxData.getProof());
    }

    @Override
    public CrossChainRedeemMessageBoxData parse(Reader reader) {
        PublicKey25519Proposition proposition = PublicKey25519PropositionSerializer.getSerializer().parse(reader);
        CrossChainMessage ccMsg = CrossChainMessageSerializer.getSerializer().parse(reader);
        byte[] certificateDataHash = reader.getBytes(reader.getInt());
        byte[] nextCertDataHash = reader.getBytes(reader.getInt());
        byte[] scCommTreeRoot = reader.getBytes(reader.getInt());
        byte[] nextScCommTreeRoot = reader.getBytes(reader.getInt());
        byte[] proof = reader.getBytes(reader.getInt());
        return new CrossChainRedeemMessageBoxData(
                proposition,
                ccMsg,
                certificateDataHash,
                nextCertDataHash,
                scCommTreeRoot,
                nextScCommTreeRoot,
                proof
        );
    }
}