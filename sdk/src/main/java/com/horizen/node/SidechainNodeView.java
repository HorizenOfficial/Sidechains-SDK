package com.horizen.node;

import com.horizen.state.ApplicationState;
import com.horizen.wallet.ApplicationWallet;

public class SidechainNodeView {
    private NodeWallet nodeWallet;
    private NodeHistory nodeHistory;
    private NodeState nodeState;
    private NodeMemoryPool nodeMemoryPool;
    private ApplicationState applicationState;
    private ApplicationWallet applicationWallet;

    public SidechainNodeView(NodeHistory nodeHistory,
                             NodeState nodeState,
                             NodeWallet nodeWallet,
                             NodeMemoryPool nodeMemoryPool,
                             ApplicationState applicationState,
                             ApplicationWallet applicationWallet) {
        this.nodeWallet = nodeWallet;
        this.nodeHistory = nodeHistory;
        this.nodeState = nodeState;
        this.nodeMemoryPool = nodeMemoryPool;
        this.applicationState = applicationState;
        this.applicationWallet = applicationWallet;
    }

    public NodeWallet getNodeWallet() {
        return nodeWallet;
    }

    public NodeHistory getNodeHistory() {
        return nodeHistory;
    }

    public NodeState getNodeState() {
        return nodeState;
    }

    public NodeMemoryPool getNodeMemoryPool() {
        return nodeMemoryPool;
    }

    public ApplicationState getApplicationState() {
        return applicationState;
    }

    public ApplicationWallet getApplicationWallet() {
        return applicationWallet;
    }
}
