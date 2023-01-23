package com.horizen.node;

import com.horizen.SidechainNodeViewBase;
import com.horizen.block.SidechainBlock;
import com.horizen.block.SidechainBlockHeader;
import com.horizen.box.Box;
import com.horizen.chain.SidechainFeePaymentsInfo;
import com.horizen.proposition.Proposition;
import com.horizen.state.ApplicationState;
import com.horizen.transaction.BoxTransaction;
import com.horizen.wallet.ApplicationWallet;

public class SidechainNodeView implements
        SidechainNodeViewBase<
                BoxTransaction<Proposition, Box<Proposition>>,
                SidechainBlockHeader,
                SidechainBlock,
                SidechainFeePaymentsInfo,
                NodeHistory,
                NodeState,
                NodeWallet,
                NodeMemoryPool> {
    private final ApplicationState applicationState;
    private final ApplicationWallet applicationWallet;

    private final NodeHistory nodeHistory;

    private final NodeState nodeState;

    private final NodeWallet nodeWallet;

    private final NodeMemoryPool nodeMemoryPool;


    public SidechainNodeView(NodeHistory nodeHistory,
                             NodeState nodeState,
                             NodeWallet nodeWallet,
                             NodeMemoryPool nodeMemoryPool,
                             ApplicationState applicationState,
                             ApplicationWallet applicationWallet) {
        this.applicationState = applicationState;
        this.applicationWallet = applicationWallet;
        this.nodeHistory = nodeHistory;
        this.nodeState = nodeState;
        this.nodeWallet = nodeWallet;
        this.nodeMemoryPool = nodeMemoryPool;
    }


    public ApplicationState getApplicationState() {
        return applicationState;
    }

    public ApplicationWallet getApplicationWallet() {
        return applicationWallet;
    }

    @Override
    public NodeHistory getNodeHistory() {
        return nodeHistory;
    }

    @Override
    public NodeState getNodeState() {
        return nodeState;
    }

    @Override
    public NodeMemoryPool getNodeMemoryPool() {
        return nodeMemoryPool;
    }

    @Override
    public NodeWallet getNodeWallet() {
        return nodeWallet;
    }
}
