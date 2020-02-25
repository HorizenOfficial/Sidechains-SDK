package com.horizen.box.data;

import scorex.util.serialization.Reader;
import scorex.util.serialization.Writer;

public final class WithdrawalRequestBoxDataSerializer implements NoncedBoxDataSerializer<WithdrawalRequestBoxData> {

    private final static WithdrawalRequestBoxDataSerializer serializer = new WithdrawalRequestBoxDataSerializer();

    private WithdrawalRequestBoxDataSerializer() {
        super();
    }

    public static WithdrawalRequestBoxDataSerializer getSerializer() {
        return serializer;
    }

    @Override
    public void serialize(WithdrawalRequestBoxData boxData, Writer writer) {
        writer.putBytes(boxData.bytes());
    }

    @Override
    public WithdrawalRequestBoxData parse(Reader reader) {
        return WithdrawalRequestBoxData.parseBytes(reader.getBytes(reader.remaining()));
    }
}
