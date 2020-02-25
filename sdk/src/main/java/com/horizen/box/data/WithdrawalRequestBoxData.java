package com.horizen.box.data;

import com.google.common.primitives.Bytes;
import com.google.common.primitives.Longs;
import com.horizen.box.WithdrawalRequestBox;
import com.horizen.proposition.MCPublicKeyHashProposition;
import com.horizen.proposition.MCPublicKeyHashPropositionSerializer;

import java.util.Arrays;

import static com.horizen.box.data.CoreBoxesDataIdsEnum.WithdrawalRequestBoxDataId;

public final class WithdrawalRequestBoxData extends AbstractNoncedBoxData<MCPublicKeyHashProposition, WithdrawalRequestBox, WithdrawalRequestBoxData> {
    public WithdrawalRequestBoxData(MCPublicKeyHashProposition proposition, long value) {
        super(proposition, value);
    }

    @Override
    public WithdrawalRequestBox getBox(long nonce) {
        return new WithdrawalRequestBox(this, nonce);
    }

    @Override
    public byte[] bytes() {
        return Bytes.concat(
                proposition().bytes(),
                Longs.toByteArray(value())
        );
    }

    @Override
    public NoncedBoxDataSerializer serializer() {
        return WithdrawalRequestBoxDataSerializer.getSerializer();
    }

    @Override
    public byte boxDataTypeId() {
        return WithdrawalRequestBoxDataId.id();
    }

    public static WithdrawalRequestBoxData parseBytes(byte[] bytes) {
        int valueOffset = MCPublicKeyHashProposition.getLength();

        MCPublicKeyHashProposition proposition = MCPublicKeyHashPropositionSerializer.getSerializer().parseBytes(Arrays.copyOf(bytes, valueOffset));
        long value = Longs.fromByteArray(Arrays.copyOfRange(bytes, valueOffset, valueOffset + Longs.BYTES));

        return new WithdrawalRequestBoxData(proposition, value);
    }
}
