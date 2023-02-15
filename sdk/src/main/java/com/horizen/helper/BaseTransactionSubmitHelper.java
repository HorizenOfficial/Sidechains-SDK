package com.horizen.helper;

import com.horizen.transaction.Transaction;

import java.util.Optional;
import java.util.function.BiConsumer;

public interface BaseTransactionSubmitHelper<TX extends Transaction> {
    void submitTransaction(TX tx) throws IllegalArgumentException;
    void asyncSubmitTransaction(TX tx, BiConsumer<Boolean, Optional<Throwable>> callback);
}

