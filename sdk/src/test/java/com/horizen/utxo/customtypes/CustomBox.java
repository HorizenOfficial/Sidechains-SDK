package com.horizen.utxo.customtypes;

import com.horizen.customtypes.CustomPublicKeyProposition;
import com.horizen.utxo.box.AbstractBox;
import com.horizen.utxo.box.BoxSerializer;


public class CustomBox extends AbstractBox<CustomPublicKeyProposition, CustomBoxData, CustomBox>
{
    public static final byte BOX_TYPE_ID = 1;

    public CustomBox (CustomBoxData boxData, long nonce) {
        super(boxData, nonce);
    }

    @Override
    public BoxSerializer serializer() {
        return CustomBoxSerializer.getSerializer();
    }

    @Override
    public byte boxTypeId() {
        return BOX_TYPE_ID;
    }

    CustomBoxData getBoxData() {
        return boxData;
    }
}
