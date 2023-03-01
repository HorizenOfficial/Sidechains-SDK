package io.horizen.utxo.box;

import io.horizen.utxo.box.data.ZenBoxData;
import io.horizen.proposition.PublicKey25519Proposition;

public final class ZenBox
    extends AbstractBox<PublicKey25519Proposition, ZenBoxData, ZenBox>
    implements CoinsBox<PublicKey25519Proposition>
{

    public ZenBox(ZenBoxData boxData, long nonce) {
        super(boxData, nonce);
    }

    @Override
    public byte boxTypeId() {
        return CoreBoxesIdsEnum.ZenBoxId.id();
    }

    @Override
    public BoxSerializer serializer() {
        return ZenBoxSerializer.getSerializer();
    }

    @Override
    public Boolean isCustom() { return false; }
}
