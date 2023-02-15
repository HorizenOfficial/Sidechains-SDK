package com.horizen.node;

import com.horizen.transaction.Transaction;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public interface NodeMemoryPoolBase<TX extends Transaction> {
    List<TX> getTransactions();

    List<TX> getTransactions(
            Comparator<TX> c,
            int limit);

    int getSize();

    Optional<TX> getTransactionById(String transactionId);
}
