package com.horizen.box.data;

import com.horizen.proposition.MCPublicKeyHashProposition;
import com.horizen.proposition.MCPublicKeyHashPropositionSerializer;
import scorex.util.serialization.Reader;
import scorex.util.serialization.Writer;


public final class WithdrawalRequestBoxDataSerializer implements BoxDataSerializer<WithdrawalRequestBoxData> {

    private final static WithdrawalRequestBoxDataSerializer serializer = new WithdrawalRequestBoxDataSerializer();

    private WithdrawalRequestBoxDataSerializer() {
        super();
    }

    public static WithdrawalRequestBoxDataSerializer getSerializer() {
        return serializer;
    }

    @Override
    public void serialize(WithdrawalRequestBoxData boxData, Writer writer) {
        boxData.proposition().serializer().serialize(boxData.proposition(), writer);
        writer.putLong(boxData.value());
    }

    @Override
    public WithdrawalRequestBoxData parse(Reader reader) {
        MCPublicKeyHashProposition proposition = MCPublicKeyHashPropositionSerializer.getSerializer().parse(reader);
        long value = reader.getLong();

        return new WithdrawalRequestBoxData(proposition, value);
    }
}
