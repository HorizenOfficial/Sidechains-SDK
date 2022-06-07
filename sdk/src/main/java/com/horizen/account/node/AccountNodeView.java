package com.horizen.account.node;

import com.horizen.SidechainNodeViewBase;
import com.horizen.account.block.AccountBlock;
import com.horizen.account.block.AccountBlockHeader;
import com.horizen.account.transaction.AccountTransaction;
import com.horizen.node.NodeWalletBase;
import com.horizen.proof.Proof;
import com.horizen.proposition.Proposition;

public class AccountNodeView implements
        SidechainNodeViewBase<
                AccountTransaction<Proposition, Proof<Proposition>>,
                AccountBlockHeader,
                AccountBlock,
                NodeAccountHistory,
                NodeAccountState,
                NodeWalletBase,
                NodeAccountMemoryPool> {

    private NodeAccountHistory nodeHistory;

    private NodeAccountState nodeState;

    private NodeWalletBase nodeWallet;

    private NodeAccountMemoryPool nodeMemoryPool;

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
