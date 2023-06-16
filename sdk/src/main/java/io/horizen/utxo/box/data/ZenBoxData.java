package io.horizen.utxo.box.data;

import io.horizen.proposition.PublicKey25519Proposition;
import io.horizen.utxo.box.ZenBox;


public final class ZenBoxData extends AbstractBoxData<PublicKey25519Proposition, ZenBox, ZenBoxData> {
    public ZenBoxData(PublicKey25519Proposition proposition, long value) {
        super(proposition, value);
    }

    @Override
    public ZenBox getBox(long nonce) {
        return new ZenBox(this, nonce);
    }

    @Override
    public BoxDataSerializer serializer() {
        return ZenBoxDataSerializer.getSerializer();
    }
}
