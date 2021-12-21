package com.horizen.box;

import com.google.common.primitives.Bytes;
import com.google.common.primitives.Longs;
import com.horizen.box.data.ZenBoxData;
import com.horizen.box.data.ZenBoxDataSerializer;
import com.horizen.proposition.PublicKey25519Proposition;

import java.util.Arrays;

import static com.horizen.box.CoreBoxesIdsEnum.ZenBoxId;

public final class ZenBox
    extends AbstractBox<PublicKey25519Proposition, ZenBoxData, ZenBox>
    implements CoinsBox<PublicKey25519Proposition>
{

    public ZenBox(ZenBoxData boxData, long nonce) {
        super(boxData, nonce);
    }

    @Override
    public byte boxTypeId() {
        return ZenBoxId.id();
    }

    @Override
    public byte[] bytes() {
        return Bytes.concat(Longs.toByteArray(nonce), ZenBoxDataSerializer.getSerializer().toBytes(boxData));
    }

    @Override
    public BoxSerializer serializer() {
        return ZenBoxSerializer.getSerializer();
    }

    public static ZenBox parseBytes(byte[] bytes) {
        long nonce = Longs.fromByteArray(Arrays.copyOf(bytes, Longs.BYTES));
        ZenBoxData boxData = ZenBoxDataSerializer.getSerializer().parseBytes(Arrays.copyOfRange(bytes, Longs.BYTES, bytes.length));

        return new ZenBox(boxData, nonce);
    }

    @Override
    public Boolean isCustom() { return false; }
}
