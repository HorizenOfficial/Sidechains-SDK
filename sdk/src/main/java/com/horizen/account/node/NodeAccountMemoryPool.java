package com.horizen.account.node;

import com.horizen.account.transaction.AccountTransaction;
import com.horizen.node.NodeMemoryPoolBase;
import com.horizen.proof.Proof;
import com.horizen.proposition.Proposition;

public interface NodeAccountMemoryPool extends NodeMemoryPoolBase<AccountTransaction<Proposition, Proof<Proposition>>> {
}
