package com.horizen.transaction;


public abstract class Transaction extends scorex.core.transaction.Transaction
{
    @Override
    public final byte modifierTypeId() {
        return super.modifierTypeId();
    }

    @Override
    // TO DO: maybe we need to provide our own implementation
    public final String id() {
        return super.id();
    }

    @Override
    public abstract byte[] bytes();

    @Override
    public abstract byte[] messageToSign();

    @Override
    public abstract TransactionSerializer serializer();

    public abstract byte transactionTypeId();
}