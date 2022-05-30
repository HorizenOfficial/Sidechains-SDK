package com.horizen.account.node;

import com.horizen.AbstractSidechainNodeViewHolder;
import com.horizen.node.NodeState;
import com.horizen.node.NodeWalletBase;

public class AccountNodeView extends AbstractSidechainNodeViewHolder.SidechainNodeViewBase<NodeAccountHistory,NodeState,NodeWalletBase,NodeAccountMemoryPool> {

    public AccountNodeView(NodeAccountHistory nodeHistory,
                           NodeState nodeState,
                           NodeWalletBase nodeWallet,
                           NodeAccountMemoryPool nodeMemoryPool) {
       super(nodeHistory,nodeState,nodeWallet,nodeMemoryPool);
     }


  }
