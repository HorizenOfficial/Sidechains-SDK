package com.horizen.node;

import com.horizen.box.Box;
import com.horizen.proposition.Proposition;
import com.horizen.transaction.BoxTransaction;

import java.util.List;
import java.util.Optional;

public interface NodeMemoryPool {

    List<BoxTransaction<? extends Proposition, ? extends Box<?>>> allTransactions();

    List<BoxTransaction<? extends Proposition, ? extends Box<?>>> getMemoryPoolSortedByFee(int limit);

    int getMemoryPoolSize();

    Optional<BoxTransaction<? extends Proposition, ? extends Box<?>>> getTransactionByid(String transactionId);

}
