package com.horizen.account.node;

import com.horizen.account.transaction.AccountTransaction;
import com.horizen.box.Box;
import com.horizen.node.NodeMemoryPoolBase;
import com.horizen.proof.Proof;
import com.horizen.proposition.Proposition;
import com.horizen.transaction.BoxTransaction;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;


public interface NodeAccountMemoryPool extends NodeMemoryPoolBase<AccountTransaction<Proposition, Proof<Proposition>>> {
    List<AccountTransaction<Proposition, Proof<Proposition>>> getTransactions();

    List<AccountTransaction<Proposition, Proof<Proposition>>> getTransactions(
            Comparator<AccountTransaction<Proposition, Proof<Proposition>>> c,
            int limit);

    int getSize();

    Optional<AccountTransaction<Proposition, Proof<Proposition>>> getTransactionById(String transactionId);
}
