package io.horizen.account.node;

import io.horizen.account.block.AccountBlock;
import io.horizen.account.block.AccountBlockHeader;
import io.horizen.account.chain.AccountFeePaymentsInfo;
import io.horizen.account.transaction.AccountTransaction;
import io.horizen.node.NodeHistoryBase;
import io.horizen.proof.Proof;
import io.horizen.proposition.Proposition;

public interface NodeAccountHistory extends NodeHistoryBase<
        AccountTransaction<Proposition, Proof<Proposition>>,
        AccountBlockHeader,
        AccountBlock,
        AccountFeePaymentsInfo> {

}
