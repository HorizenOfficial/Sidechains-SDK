package com.horizen.node;

public interface NodeView {

    NodeHistory getNodeHistory();

    NodeState getNodeState();

    NodeMemoryPool getNodeMemoryPool();

    NodeWallet getNodeWallet();
}
