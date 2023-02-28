package io.horizen.utxo.node;

import com.horizen.utxo.block.SidechainBlock;
import com.horizen.utxo.block.SidechainBlockHeader;
import com.horizen.node.NodeHistoryBase;
import com.horizen.utxo.box.Box;
import com.horizen.utxo.chain.SidechainFeePaymentsInfo;
import com.horizen.proposition.Proposition;
import com.horizen.utxo.transaction.BoxTransaction;


public interface NodeHistory extends NodeHistoryBase<
        BoxTransaction<Proposition, Box<Proposition>>,
        SidechainBlockHeader,
        SidechainBlock,
        SidechainFeePaymentsInfo> {
}

