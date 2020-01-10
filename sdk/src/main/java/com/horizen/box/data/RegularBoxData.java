package com.horizen.box.data;

import com.google.common.primitives.Bytes;
import com.google.common.primitives.Longs;
import com.horizen.proposition.PublicKey25519Proposition;
import com.horizen.proposition.PublicKey25519PropositionSerializer;

import java.util.Arrays;

public final class RegularBoxData extends AbstractBoxData<PublicKey25519Proposition> {
    public RegularBoxData(PublicKey25519Proposition proposition, long value) {
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
        return RegularBoxDataSerializer.getSerializer();
    }

    public static RegularBoxData parseBytes(byte[] bytes) {
        int valueOffset = PublicKey25519Proposition.getLength();

        PublicKey25519Proposition proposition = PublicKey25519PropositionSerializer.getSerializer().parseBytes(Arrays.copyOf(bytes, valueOffset));
        long value = Longs.fromByteArray(Arrays.copyOfRange(bytes, valueOffset, valueOffset + Longs.BYTES));

        return new RegularBoxData(proposition, value);
    }
}
