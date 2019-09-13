package com.horizen.node;

public class SidechainNodeView {
    private NodeWallet nodeWallet;
    private NodeHistory nodeHistory;
    private NodeState nodeState;
    private NodeMemoryPool nodeMemoryPool;

    public SidechainNodeView(NodeHistory nodeHistory, NodeState nodeState, NodeWallet nodeWallet, NodeMemoryPool nodeMemoryPool) {
        this.nodeWallet = nodeWallet;
        this.nodeHistory = nodeHistory;
        this.nodeState = nodeState;
        this.nodeMemoryPool = nodeMemoryPool;
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
}
