package com.horizen.utxo.customtypes;

import com.horizen.customtypes.CustomPublicKeyProposition;
import com.horizen.utxo.box.data.AbstractBoxData;
import com.horizen.utxo.box.data.BoxDataSerializer;


public class CustomBoxData extends AbstractBoxData<CustomPublicKeyProposition, CustomBox, CustomBoxData> {
    public CustomBoxData(CustomPublicKeyProposition proposition, long value) {
        super(proposition, value);
    }

    @Override
    public CustomBox getBox(long nonce) {
        return new CustomBox(this, nonce);
    }

    @Override
    public BoxDataSerializer serializer() {
        return CustomBoxDataSerializer.getSerializer();
    }
}
