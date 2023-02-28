package io.horizen;

import com.horizen.block.SidechainBlockBase;
import com.horizen.block.SidechainBlockHeaderBase;
import com.horizen.chain.AbstractFeePaymentsInfo;
import com.horizen.node.NodeHistoryBase;
import com.horizen.node.NodeMemoryPoolBase;
import com.horizen.node.NodeStateBase;
import com.horizen.node.NodeWalletBase;
import com.horizen.transaction.Transaction;

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