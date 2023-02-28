package io.horizen.account.node;

import io.horizen.account.transaction.AccountTransaction;
import io.horizen.node.NodeMemoryPoolBase;
import io.horizen.proof.Proof;
import io.horizen.proposition.Proposition;

public interface NodeAccountMemoryPool extends NodeMemoryPoolBase<AccountTransaction<Proposition, Proof<Proposition>>> {
}
