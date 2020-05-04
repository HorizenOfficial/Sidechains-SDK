package com.horizen.customtypes;

import com.google.common.primitives.Bytes;
import com.google.common.primitives.Longs;
import com.horizen.box.data.AbstractNoncedBoxData;
import com.horizen.box.data.NoncedBoxDataSerializer;
import java.util.Arrays;

public class CustomBoxData extends AbstractNoncedBoxData<CustomPublicKeyProposition, CustomBox, CustomBoxData> {
    public CustomBoxData(CustomPublicKeyProposition proposition, long value) {
        super(proposition, value);
    }

    public static final byte DATA_TYPE_ID = 0;

    @Override
    public CustomBox getBox(long nonce) {
        return new CustomBox(this, nonce);
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
        return CustomBoxDataSerializer.getSerializer();
    }

    @Override
    public byte boxDataTypeId() {
        return DATA_TYPE_ID;
    }

    public static CustomBoxData parseBytes(byte[] bytes) {
        int valueOffset = CustomPublicKeyProposition.getLength();

        CustomPublicKeyProposition proposition = CustomPublicKeyPropositionSerializer.getSerializer().parseBytes(Arrays.copyOf(bytes, valueOffset));
        long value = Longs.fromByteArray(Arrays.copyOfRange(bytes, valueOffset, valueOffset + Longs.BYTES));

        return new CustomBoxData(proposition, value);
    }
}
