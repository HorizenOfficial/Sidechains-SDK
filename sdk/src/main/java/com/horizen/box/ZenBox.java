package com.horizen.box;

import com.horizen.box.data.ZenBoxData;
import com.horizen.proposition.PublicKey25519Proposition;
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
    public BoxSerializer serializer() {
        return ZenBoxSerializer.getSerializer();
    }

    @Override
    public Boolean isCustom() { return false; }
}
