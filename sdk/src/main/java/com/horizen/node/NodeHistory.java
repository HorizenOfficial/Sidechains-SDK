package com.horizen.node;


import com.horizen.block.SidechainBlock;
import com.horizen.block.SidechainBlockHeader;
import com.horizen.box.Box;
import com.horizen.proposition.Proposition;
import com.horizen.transaction.BoxTransaction;

import java.util.Optional;

public interface NodeHistory
        extends NodeHistoryBase<BoxTransaction<Proposition, Box<Proposition>>, SidechainBlockHeader, SidechainBlock> {

    @Override
    Optional<SidechainBlock> getBlockById(String blockId);

    @Override
    SidechainBlock getBestBlock();

    Optional<BoxTransaction<Proposition, Box<Proposition>>> searchTransactionInsideSidechainBlock(String transactionId, String blockId);

    Optional<BoxTransaction<Proposition, Box<Proposition>>> searchTransactionInsideBlockchain(String transactionId);
}

