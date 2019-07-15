/*
package com.horizen.transaction;


import scorex.crypto.hash.Blake2b256;
import scorex.util.encode.Base16;

public abstract class Transaction implements scorex.core.transaction.Transaction
{

    @Override
    public String id() {
        return Base16.encode(Blake2b256.hash(messageToSign()));
    }

    @Override
    public abstract byte[] bytes();

    @Override
    public abstract byte[] messageToSign();

    @Override
    public abstract TransactionSerializer serializer();

    public abstract byte transactionTypeId();
}
*/
