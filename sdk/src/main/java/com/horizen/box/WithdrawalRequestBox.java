package com.horizen.box;

import com.google.common.primitives.Bytes;
import com.google.common.primitives.Longs;
import com.horizen.box.data.WithdrawalRequestBoxData;
import com.horizen.box.data.WithdrawalRequestBoxDataSerializer;
import com.horizen.proposition.MCPublicKeyHashProposition;

import java.util.Arrays;

import static com.horizen.box.CoreBoxesIdsEnum.WithdrawalRequestBoxId;

public final class WithdrawalRequestBox
    extends AbstractNoncedBox<MCPublicKeyHashProposition, WithdrawalRequestBoxData, WithdrawalRequestBox>
{
    public WithdrawalRequestBox(WithdrawalRequestBoxData boxData, long nonce) {
        super(boxData, nonce);
    }

    @Override
    public byte boxTypeId() {
        return WithdrawalRequestBoxId.id();
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
