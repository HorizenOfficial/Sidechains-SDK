package io.horizen.utxo.node;

import io.horizen.node.NodeHistoryBase;
import io.horizen.proposition.Proposition;
import io.horizen.utxo.block.SidechainBlock;
import io.horizen.utxo.block.SidechainBlockHeader;
import io.horizen.utxo.box.Box;
import io.horizen.utxo.chain.SidechainFeePaymentsInfo;
import io.horizen.utxo.transaction.BoxTransaction;


public interface NodeHistory extends NodeHistoryBase<
        BoxTransaction<Proposition, Box<Proposition>>,
        SidechainBlockHeader,
        SidechainBlock,
        SidechainFeePaymentsInfo> {
}

