package com.horizen.box.data;

import com.google.common.primitives.Bytes;
import com.google.common.primitives.Longs;
import com.horizen.box.RegularBox;
import com.horizen.proposition.PublicKey25519Proposition;
import com.horizen.proposition.PublicKey25519PropositionSerializer;

import java.util.Arrays;

import static com.horizen.box.data.CoreBoxesDataIdsEnum.RegularBoxDataId;

public final class RegularBoxData extends AbstractNoncedBoxData<PublicKey25519Proposition, RegularBox, RegularBoxData> {
    public RegularBoxData(PublicKey25519Proposition proposition, long value) {
        super(proposition, value);
    }

    @Override
    public RegularBox getBox(long nonce) {
        return new RegularBox(this, nonce);
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
        return RegularBoxDataSerializer.getSerializer();
    }

    @Override
    public byte boxDataTypeId() {
        return RegularBoxDataId.id();
    }

    public static RegularBoxData parseBytes(byte[] bytes) {
        int valueOffset = PublicKey25519Proposition.getLength();

        PublicKey25519Proposition proposition = PublicKey25519PropositionSerializer.getSerializer().parseBytes(Arrays.copyOf(bytes, valueOffset));
        long value = Longs.fromByteArray(Arrays.copyOfRange(bytes, valueOffset, valueOffset + Longs.BYTES));

        return new RegularBoxData(proposition, value);
    }
}
