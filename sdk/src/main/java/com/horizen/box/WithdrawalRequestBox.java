package com.horizen.box;

import com.google.common.primitives.Bytes;
import com.google.common.primitives.Longs;
import com.horizen.box.data.WithdrawalRequestBoxData;
import com.horizen.box.data.WithdrawalRequestBoxDataSerializer;
import com.horizen.proposition.MCPublicKeyHashProposition;

import java.util.Arrays;

public final class WithdrawalRequestBox
    extends AbstractNoncedBox<MCPublicKeyHashProposition, WithdrawalRequestBoxData>
    implements CoinsBox<MCPublicKeyHashProposition>
{

    public static final byte BOX_TYPE_ID = 2;

    public WithdrawalRequestBox(WithdrawalRequestBoxData boxData,
                                long nonce)
    {
        super(boxData, nonce);
    }

    @Override
    public byte boxTypeId() {
        return BOX_TYPE_ID;
    }


    @Override
    public BoxSerializer serializer() {
        return WithdrawalRequestBoxSerializer.getSerializer();
    }

    @Override
    public byte[] bytes() {
        return Bytes.concat(Longs.toByteArray(nonce), WithdrawalRequestBoxDataSerializer.getSerializer().toBytes(boxData));
    }

    public static WithdrawalRequestBox parseBytes(byte[] bytes) {
        long nonce = Longs.fromByteArray(Arrays.copyOf(bytes, Longs.BYTES));
        WithdrawalRequestBoxData boxData = WithdrawalRequestBoxDataSerializer.getSerializer().parseBytes(Arrays.copyOfRange(bytes, Longs.BYTES, bytes.length));

        return new WithdrawalRequestBox(boxData, nonce);
    }
}
