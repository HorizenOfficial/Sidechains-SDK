package io.horizen.utxo.box;

import com.horizen.utxo.box.data.WithdrawalRequestBoxData;
import com.horizen.proposition.MCPublicKeyHashProposition;


public final class WithdrawalRequestBox
    extends AbstractBox<MCPublicKeyHashProposition, WithdrawalRequestBoxData, WithdrawalRequestBox>
{
    public WithdrawalRequestBox(WithdrawalRequestBoxData boxData, long nonce) {
        super(boxData, nonce);
    }

    @Override
    public byte boxTypeId() {
        return CoreBoxesIdsEnum.WithdrawalRequestBoxId.id();
    }
    
    @Override
    public BoxSerializer serializer() {
        return WithdrawalRequestBoxSerializer.getSerializer();
    }

    @Override
    public Boolean isCustom() { return false; }
}
