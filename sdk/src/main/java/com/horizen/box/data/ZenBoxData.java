package com.horizen.box.data;

import com.google.common.primitives.Bytes;
import com.google.common.primitives.Longs;
import com.horizen.box.ZenBox;
import com.horizen.proposition.PublicKey25519Proposition;
import com.horizen.proposition.PublicKey25519PropositionSerializer;

import java.util.Arrays;

import static com.horizen.box.data.CoreBoxesDataIdsEnum.ZenBoxDataId;

public final class ZenBoxData extends AbstractNoncedBoxData<PublicKey25519Proposition, ZenBox, ZenBoxData> {
    public ZenBoxData(PublicKey25519Proposition proposition, long value) {
        super(proposition, value);
    }

    @Override
    public ZenBox getBox(long nonce) {
        return new ZenBox(this, nonce);
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
        return ZenBoxDataSerializer.getSerializer();
    }

    @Override
    public byte boxDataTypeId() {
        return ZenBoxDataId.id();
    }

    public static ZenBoxData parseBytes(byte[] bytes) {
        int valueOffset = PublicKey25519Proposition.getLength();

        PublicKey25519Proposition proposition = PublicKey25519PropositionSerializer.getSerializer().parseBytes(Arrays.copyOf(bytes, valueOffset));
        long value = Longs.fromByteArray(Arrays.copyOfRange(bytes, valueOffset, valueOffset + Longs.BYTES));

        return new ZenBoxData(proposition, value);
    }
}
