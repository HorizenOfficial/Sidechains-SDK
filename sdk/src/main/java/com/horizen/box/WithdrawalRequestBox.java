package com.horizen.box;

import com.horizen.box.data.WithdrawalRequestBoxData;
import com.horizen.proposition.MCPublicKeyHashProposition;
import static com.horizen.box.CoreBoxesIdsEnum.WithdrawalRequestBoxId;


public final class WithdrawalRequestBox
    extends AbstractBox<MCPublicKeyHashProposition, WithdrawalRequestBoxData, WithdrawalRequestBox>
{
    public WithdrawalRequestBox(WithdrawalRequestBoxData boxData, long nonce) {
        super(boxData, nonce);
    }

    @Override
    public byte boxTypeId() {
        return WithdrawalRequestBoxId.id();
    }
    
    @Override
    public BoxSerializer serializer() {
        return WithdrawalRequestBoxSerializer.getSerializer();
    }

    @Override
    public Boolean isCustom() { return false; }
}
