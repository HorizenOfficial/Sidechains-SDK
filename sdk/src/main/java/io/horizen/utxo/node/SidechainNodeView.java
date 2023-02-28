package io.horizen.utxo.node;

import io.horizen.SidechainNodeViewBase;
import io.horizen.utxo.block.SidechainBlock;
import io.horizen.utxo.block.SidechainBlockHeader;
import io.horizen.utxo.box.Box;
import io.horizen.utxo.chain.SidechainFeePaymentsInfo;
import io.horizen.proposition.Proposition;
import io.horizen.utxo.state.ApplicationState;
import io.horizen.utxo.transaction.BoxTransaction;
import io.horizen.utxo.wallet.ApplicationWallet;

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
