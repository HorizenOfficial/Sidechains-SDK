package com.horizen.transaction;

public abstract class Transaction extends scorex.core.transaction.Transaction
{
    @Override
    // TO DO: think more about supertagged lib representation in java
    public final byte modifierTypeId() {
        return super.modifierTypeId();
    }

    @Override
    public final String id() {
        return super.id();
    }

    @Override
    public final byte[] bytes() {
        return serializer().toBytes(this);
    }

    @Override
    public abstract byte[] messageToSign();

    @Override
    public TransactionSerializer serializer() { return null; };

    // TO DO: maybe we need to return scorex.core.ModifierTypeId
    public abstract byte transactionTypeId();
}