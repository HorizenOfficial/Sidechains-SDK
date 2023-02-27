package com.horizen.utxo.box;

import com.horizen.utxo.box.data.WithdrawalRequestBoxData;
import com.horizen.utxo.box.data.WithdrawalRequestBoxDataSerializer;
import sparkz.util.serialization.Reader;
import sparkz.util.serialization.Writer;

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
        writer.putLong(box.nonce);
        box.boxData.serializer().serialize(box.boxData, writer);
    }

    @Override
    public WithdrawalRequestBox parse(Reader reader) {
        Long nonce = reader.getLong();
        WithdrawalRequestBoxData boxData = WithdrawalRequestBoxDataSerializer.getSerializer().parse(reader);

        return new WithdrawalRequestBox(boxData, nonce);
    }
}
