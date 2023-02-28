package io.horizen.utxo.box.data;

import io.horizen.utxo.box.WithdrawalRequestBox;
import io.horizen.proposition.MCPublicKeyHashProposition;


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
