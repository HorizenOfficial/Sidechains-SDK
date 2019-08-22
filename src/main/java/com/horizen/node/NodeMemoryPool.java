package com.horizen.node;

import com.horizen.box.Box;
import com.horizen.proposition.Proposition;
import com.horizen.transaction.BoxTransaction;
import scala.sys.Prop;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public interface NodeMemoryPool {
    List<BoxTransaction<Proposition, Box<Proposition>>> getAllTransactions();

    List<BoxTransaction<Proposition, Box<Proposition>>> getTransactionsSortedByFee(int limit);

    List<BoxTransaction<Proposition, Box<Proposition>>> getTransactionsSortedBy(
            Comparator<BoxTransaction<Proposition, Box<Proposition>>> c,
            int limit);

    int getSize();

    Optional<BoxTransaction<Proposition, Box<Proposition>>> getTransactionById(String transactionId);
}
