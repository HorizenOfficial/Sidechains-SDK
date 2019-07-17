package com.horizen.node;

import com.horizen.block.Block;
import com.horizen.transaction.BoxTransaction;

import java.util.Optional;

public interface NodeViewHolder
        <H extends NodeHistory, S extends NodeState, W extends NodeWallet, M extends NodeMemoryPool,
                B extends Block<BT,?,?>, BT extends BoxTransaction<?,?>> {

    NodeView<H, S, W, M> getCurrentNodeView();

    NodeView<H, S, W, M> genesisState();

    Optional<NodeView<H, S, W, M>> restoreState();

    void updateNodeView(Optional<H> updatedHistory, Optional<S> updatedState, Optional<W> updatedWallet, Optional<M> updatedMempool);
}
