package com.horizen.box;

import com.google.common.primitives.Bytes;
import com.google.common.primitives.Longs;
import com.horizen.box.data.RegularBoxData;
import com.horizen.box.data.RegularBoxDataSerializer;
import com.horizen.proposition.PublicKey25519Proposition;

import java.util.Arrays;

public final class RegularBox
    extends AbstractNoncedBox<PublicKey25519Proposition, RegularBoxData>
    implements CoinsBox<PublicKey25519Proposition>
{

    public static final byte BOX_TYPE_ID = 1;

    public RegularBox(RegularBoxData boxData, long nonce)
    {
        super(boxData, nonce);
    }

    @Override
    public byte boxTypeId() {
        return BOX_TYPE_ID;
    }

    @Override
    public byte[] bytes() {
        return Bytes.concat(Longs.toByteArray(nonce), RegularBoxDataSerializer.getSerializer().toBytes(boxData));
    }

    @Override
    public BoxSerializer serializer() {
        return RegularBoxSerializer.getSerializer();
    }

    public static RegularBox parseBytes(byte[] bytes) {
        long nonce = Longs.fromByteArray(Arrays.copyOf(bytes, Longs.BYTES));
        RegularBoxData boxData = RegularBoxDataSerializer.getSerializer().parseBytes(Arrays.copyOfRange(bytes, Longs.BYTES, bytes.length));

        return new RegularBox(boxData, nonce);
    }
}
