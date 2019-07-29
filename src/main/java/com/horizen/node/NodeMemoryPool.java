package com.horizen.node;

import com.horizen.box.Box;
import com.horizen.proposition.Proposition;
import com.horizen.transaction.BoxTransaction;

import java.util.List;
import java.util.Optional;

public interface NodeMemoryPool {
    List<BoxTransaction<Proposition, Box<Proposition>>> allTransactions();

    List<BoxTransaction<Proposition, Box<Proposition>>> getTransactionsSortedByFee(int limit);

    int getSize();

    Optional<BoxTransaction<Proposition, Box<Proposition>>> getTransactionByid(String transactionId);
}
