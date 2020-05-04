package com.horizen.customtypes;

import com.google.common.primitives.Bytes;
import com.google.common.primitives.Longs;
import com.horizen.box.AbstractNoncedBox;
import com.horizen.box.BoxSerializer;

import java.util.Arrays;

public class CustomBox extends AbstractNoncedBox<CustomPublicKeyProposition, CustomBoxData, CustomBox>
{
    public static final byte BOX_TYPE_ID = 1;

    public CustomBox (CustomBoxData boxData, long nonce) {
        super(boxData, nonce);
    }

    @Override
    public byte[] bytes() {
        return Bytes.concat(Longs.toByteArray(nonce), CustomBoxDataSerializer.getSerializer().toBytes(boxData));
    }

    @Override
    public BoxSerializer serializer() {
        return CustomBoxSerializer.getSerializer();
    }

    @Override
    public byte boxTypeId() {
        return BOX_TYPE_ID;
    }

    public static CustomBox parseBytes(byte[] bytes) {
        long nonce = Longs.fromByteArray(Arrays.copyOf(bytes, Longs.BYTES));
        CustomBoxData boxData = CustomBoxDataSerializer.getSerializer().parseBytes(Arrays.copyOfRange(bytes, Longs.BYTES, bytes.length));

        return new CustomBox(boxData, nonce);
    }
}
