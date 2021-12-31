package com.horizen.box.data;

import com.horizen.proposition.PublicKey25519Proposition;
import com.horizen.proposition.PublicKey25519PropositionSerializer;
import com.horizen.proposition.VrfPublicKey;
import com.horizen.proposition.VrfPublicKeySerializer;
import scorex.util.serialization.Reader;
import scorex.util.serialization.Writer;


public final class ForgerBoxDataSerializer implements BoxDataSerializer<ForgerBoxData> {

    private final static ForgerBoxDataSerializer serializer = new ForgerBoxDataSerializer();

    private ForgerBoxDataSerializer() {
        super();
    }

    public static ForgerBoxDataSerializer getSerializer() {
        return serializer;
    }

    @Override
    public void serialize(ForgerBoxData boxData, Writer writer) {
        boxData.proposition().serializer().serialize(boxData.proposition(), writer);
        writer.putLong(boxData.value());
        boxData.blockSignProposition().serializer().serialize(boxData.blockSignProposition(), writer);
        boxData.vrfPublicKey().serializer().serialize(boxData.vrfPublicKey(), writer);
    }

    @Override
    public ForgerBoxData parse(Reader reader) {
        PublicKey25519Proposition proposition = PublicKey25519PropositionSerializer.getSerializer().parse(reader);
        long value = reader.getLong();
        PublicKey25519Proposition blockSignProposition = PublicKey25519PropositionSerializer.getSerializer().parse(reader);
        VrfPublicKey vrfPublicKey = VrfPublicKeySerializer.getSerializer().parse(reader);

        return new ForgerBoxData(proposition, value, blockSignProposition, vrfPublicKey);
    }
}