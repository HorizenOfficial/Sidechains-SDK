package com.horizen.box.data;

import com.google.common.primitives.Bytes;
import com.google.common.primitives.Longs;
import com.horizen.proposition.MCPublicKeyHashProposition;
import com.horizen.proposition.MCPublicKeyHashPropositionSerializer;

import java.util.Arrays;

public final class WithdrawalRequestBoxData extends AbstractBoxData<MCPublicKeyHashProposition> {
    public WithdrawalRequestBoxData(MCPublicKeyHashProposition proposition, long value) {
        super(proposition, value);
    }

    @Override
    public byte[] bytes() {
        return Bytes.concat(
                proposition().bytes(),
                Longs.toByteArray(value())
        );
    }

    @Override
    public BoxDataSerializer serializer() {
        return WithdrawalRequestBoxDataSerializer.getSerializer();
    }

    public static WithdrawalRequestBoxData parseBytes(byte[] bytes) {
        int valueOffset = MCPublicKeyHashProposition.getLength();

        MCPublicKeyHashProposition proposition = MCPublicKeyHashPropositionSerializer.getSerializer().parseBytes(Arrays.copyOf(bytes, valueOffset));
        long value = Longs.fromByteArray(Arrays.copyOfRange(bytes, valueOffset, valueOffset + Longs.BYTES));

        return new WithdrawalRequestBoxData(proposition, value);
    }
}