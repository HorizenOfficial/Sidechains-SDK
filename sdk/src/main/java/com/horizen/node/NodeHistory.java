package com.horizen.node;

import com.horizen.block.SidechainBlock;
import com.horizen.block.SidechainBlockHeader;
import com.horizen.box.Box;
import com.horizen.chain.SidechainFeePaymentsInfo;
import com.horizen.proposition.Proposition;
import com.horizen.transaction.BoxTransaction;


public interface NodeHistory extends NodeHistoryBase<
        BoxTransaction<Proposition, Box<Proposition>>,
        SidechainBlockHeader,
        SidechainBlock,
        SidechainFeePaymentsInfo> {
}

