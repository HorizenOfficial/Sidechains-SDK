package com.horizen.box.data;

import com.horizen.box.WithdrawalRequestBox;
import com.horizen.proposition.MCPublicKeyHashProposition;


public final class WithdrawalRequestBoxData extends AbstractBoxData<MCPublicKeyHashProposition, WithdrawalRequestBox, WithdrawalRequestBoxData> {
    public WithdrawalRequestBoxData(MCPublicKeyHashProposition proposition, long value) {
        super(proposition, value);
    }

    @Override
    public WithdrawalRequestBox getBox(long nonce) {
        return new WithdrawalRequestBox(this, nonce);
    }

    @Override
    public BoxDataSerializer serializer() {
        return WithdrawalRequestBoxDataSerializer.getSerializer();
    }
}
