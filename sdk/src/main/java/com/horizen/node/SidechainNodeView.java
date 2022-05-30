package com.horizen.node;

import com.horizen.AbstractSidechainNodeViewHolder;
import com.horizen.SidechainNodeViewBase;
import com.horizen.state.ApplicationState;
import com.horizen.wallet.ApplicationWallet;

public class SidechainNodeView extends SidechainNodeViewBase<NodeHistory,NodeState,NodeWallet,NodeMemoryPool> {
    private ApplicationState applicationState;
    private ApplicationWallet applicationWallet;


    public SidechainNodeView(H nodeHistory,
                             S nodeState,
                             W nodeWallet,
                             P nodeMemoryPool,
                             ApplicationState applicationState,
                             ApplicationWallet applicationWallet) {
        super(nodeHistory,nodeState,nodeWallet,nodeMemoryPool);
        this.applicationState = applicationState;
        this.applicationWallet = applicationWallet;
     }

//    public H getNodeHistory() {
//        return nodeHistory();
//    }
//
//    public S getNodeState() {
//        return nodeState();
//    }
//
//    public P getNodeMemoryPool() {
//        return nodeMemoryPool();
//    }
//
//    public W getNodeWallet() {
//        return this.nodeWallet();
//    }

    public ApplicationState getApplicationState() {
        return applicationState;
    }

    public ApplicationWallet getApplicationWallet() {
        return applicationWallet;
    }
}
