package io.horizen.account.api.http;

import io.horizen.account.block.AccountBlock;
import io.horizen.account.block.AccountBlockHeader;
import io.horizen.account.chain.AccountFeePaymentsInfo;
import io.horizen.account.node.AccountNodeView;
import io.horizen.account.node.NodeAccountHistory;
import io.horizen.account.node.NodeAccountMemoryPool;
import io.horizen.account.node.NodeAccountState;
import io.horizen.account.transaction.AccountTransaction;
import io.horizen.api.http.ApplicationBaseApiGroup;
import io.horizen.node.NodeWalletBase;
import io.horizen.proof.Proof;
import io.horizen.proposition.Proposition;

public abstract class AccountApplicationApiGroup extends ApplicationBaseApiGroup<
        AccountTransaction<Proposition, Proof<Proposition>>,
        AccountBlockHeader,
        AccountBlock,
        AccountFeePaymentsInfo,
        NodeAccountHistory,
        NodeAccountState,
        NodeWalletBase,
        NodeAccountMemoryPool,
        AccountNodeView
        > {}