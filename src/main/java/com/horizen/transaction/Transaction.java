package com.horizen.transaction;

public abstract class Transaction extends scorex.core.transaction.Transaction
{
    @Override
    public final scorex.core.ModifierTypeId modifierTypeId() {
        return (scorex.core.ModifierTypeId)super.modifierTypeId();
    }

    @Override
    public final scorex.core.ModifierId id() {
        return (scorex.core.ModifierId)super.id();
    }

    @Override
    public final byte[] bytes() {
        return serializer().toBytes(this);
    }

    @Override
    public abstract byte[] messageToSign();

    @Override
    public abstract TransactionSerializer serializer();

    public abstract scorex.core.ModifierTypeId transactionTypeId();
}