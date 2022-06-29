package com.horizen.node;

import com.horizen.box.Box;
import com.horizen.proposition.Proposition;
import com.horizen.transaction.BoxTransaction;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public interface NodeMemoryPool {
    List<BoxTransaction<Proposition, Box<Proposition>>> getTransactions();

    List<BoxTransaction<Proposition, Box<Proposition>>> getTransactions(
            Comparator<BoxTransaction<Proposition, Box<Proposition>>> c,
            int limit);

    /**
     * Get transactions sorted by fee, from the lowest one in ascending order
     * @deprecated use {@link #getTransactionsSortedByFeeRate(int)} instead (note that the order will be the opposite there)
     */
    @Deprecated
    List<BoxTransaction<Proposition, Box<Proposition>>> getTransactionsSortedByFee(int limit);

    /**
     * Get transactions sorted by feeRate, from the highest one in descending order
     */
    List<BoxTransaction<Proposition, Box<Proposition>>> getTransactionsSortedByFeeRate(int limit);

    int getSize();

    Optional<BoxTransaction<Proposition, Box<Proposition>>> getTransactionById(String transactionId);
}
