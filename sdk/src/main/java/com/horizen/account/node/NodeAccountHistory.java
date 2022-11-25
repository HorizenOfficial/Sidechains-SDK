package com.horizen.account.node;

import com.horizen.account.block.AccountBlock;
import com.horizen.account.block.AccountBlockHeader;
import com.horizen.account.chain.AccountFeePaymentsInfo;
import com.horizen.account.transaction.AccountTransaction;
import com.horizen.node.NodeHistoryBase;
import com.horizen.proof.Proof;
import com.horizen.proposition.Proposition;

import java.util.Optional;

public interface NodeAccountHistory extends NodeHistoryBase<
        AccountTransaction<Proposition, Proof<Proposition>>,
        AccountBlockHeader,
        AccountBlock,
        AccountFeePaymentsInfo
        > {

    Optional<AccountTransaction<Proposition, Proof<Proposition>>> searchTransactionInsideSidechainBlock(String transactionId, String blockId);

    Optional<AccountTransaction<Proposition, Proof<Proposition>>> searchTransactionInsideBlockchain(String transactionId);

}