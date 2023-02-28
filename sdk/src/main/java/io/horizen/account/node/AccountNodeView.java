package io.horizen.account.node;

import io.horizen.SidechainNodeViewBase;
import io.horizen.account.block.AccountBlock;
import io.horizen.account.block.AccountBlockHeader;
import io.horizen.account.chain.AccountFeePaymentsInfo;
import io.horizen.account.transaction.AccountTransaction;
import io.horizen.node.NodeWalletBase;
import io.horizen.proof.Proof;
import io.horizen.proposition.Proposition;

public class AccountNodeView implements
        SidechainNodeViewBase<
                AccountTransaction<Proposition, Proof<Proposition>>,
                AccountBlockHeader,
                AccountBlock,
                AccountFeePaymentsInfo,
                NodeAccountHistory,
                NodeAccountState,
                NodeWalletBase,
                NodeAccountMemoryPool> {

    private final NodeAccountHistory nodeHistory;

    private final NodeAccountState nodeState;

    private final NodeWalletBase nodeWallet;

    private final NodeAccountMemoryPool nodeMemoryPool;

    public AccountNodeView(NodeAccountHistory nodeHistory,
                           NodeAccountState nodeState,
                           NodeWalletBase nodeWallet,
                           NodeAccountMemoryPool nodeMemoryPool) {
        this.nodeHistory = nodeHistory;
        this.nodeState = nodeState;
        this.nodeWallet = nodeWallet;
        this.nodeMemoryPool = nodeMemoryPool;
    }


    @Override
    public NodeAccountHistory getNodeHistory() {
        return nodeHistory;
    }

    @Override
    public NodeAccountState getNodeState() {
        return nodeState;
    }

    @Override
    public NodeAccountMemoryPool getNodeMemoryPool() {
        return nodeMemoryPool;
    }

    @Override
    public NodeWalletBase getNodeWallet() {
        return nodeWallet;
    }
}
