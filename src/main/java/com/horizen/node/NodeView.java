package com.horizen.node;

public interface NodeView <H extends NodeHistory, S extends NodeState, W extends NodeWallet, M extends NodeMemoryPool> {

    H getNodeHistory();

    S getNodeState();

    M getNodeMemoryPool();

    W getNodeWallet();
}
