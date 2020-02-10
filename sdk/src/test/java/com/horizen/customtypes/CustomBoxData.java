package com.horizen.customtypes;

import com.google.common.primitives.Bytes;
import com.google.common.primitives.Longs;
import com.horizen.box.data.AbstractBoxData;
import com.horizen.box.data.BoxDataSerializer;
import java.util.Arrays;

public class CustomBoxData extends AbstractBoxData<CustomPublicKeyProposition, CustomBox, CustomBoxData> {
    public CustomBoxData(CustomPublicKeyProposition proposition, long value) {
        super(proposition, value);
    }

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
    public BoxDataSerializer serializer() {
        return CustomBoxDataSerializer.getSerializer();
    }

    @Override
    public byte boxDataTypeId() {
        return 0;
    }

    public static CustomBoxData parseBytes(byte[] bytes) {
        int valueOffset = CustomPublicKeyProposition.getLength();

        CustomPublicKeyProposition proposition = CustomPublicKeyPropositionSerializer.getSerializer().parseBytes(Arrays.copyOf(bytes, valueOffset));
        long value = Longs.fromByteArray(Arrays.copyOfRange(bytes, valueOffset, valueOffset + Longs.BYTES));

        return new CustomBoxData(proposition, value);
    }
}
