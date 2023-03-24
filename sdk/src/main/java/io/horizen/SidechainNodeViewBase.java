package io.horizen;

import io.horizen.block.SidechainBlockBase;
import io.horizen.block.SidechainBlockHeaderBase;
import io.horizen.chain.AbstractFeePaymentsInfo;
import io.horizen.node.NodeHistoryBase;
import io.horizen.node.NodeMemoryPoolBase;
import io.horizen.node.NodeStateBase;
import io.horizen.node.NodeWalletBase;
import io.horizen.transaction.Transaction;

public interface SidechainNodeViewBase<
        TX extends Transaction,
        H extends SidechainBlockHeaderBase,
        PM extends SidechainBlockBase<TX, H>,
        FPI extends AbstractFeePaymentsInfo,
        NH extends NodeHistoryBase<TX, H, PM, FPI>,
        NS extends NodeStateBase,
        NW extends NodeWalletBase,
        NP extends NodeMemoryPoolBase<TX>> {
    NH getNodeHistory();

    NS getNodeState();

    NP getNodeMemoryPool();

    NW getNodeWallet();

}