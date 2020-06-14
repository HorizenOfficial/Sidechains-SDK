package com.horizen.box;

import scorex.util.serialization.Reader;
import scorex.util.serialization.Writer;

public final class WithdrawalRequestBoxSerializer
    implements BoxSerializer<WithdrawalRequestBox>
{

    private static WithdrawalRequestBoxSerializer serializer;

    static {
        serializer = new WithdrawalRequestBoxSerializer();
    }

    private WithdrawalRequestBoxSerializer() {
        super();

    }

    public static WithdrawalRequestBoxSerializer getSerializer() {
        return serializer;
    }

    @Override
    public void serialize(WithdrawalRequestBox box, Writer writer) {
        writer.putBytes(box.bytes());
    }

    @Override
    public WithdrawalRequestBox parse(Reader reader) {
        return WithdrawalRequestBox.parseBytes(reader.getBytes(reader.remaining()));
    }

}
