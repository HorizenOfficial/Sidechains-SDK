package com.horizen.node;


import com.horizen.block.SidechainBlock;
import com.horizen.box.Box;

import com.horizen.proposition.Proposition;
import com.horizen.transaction.BoxTransaction;

import java.util.Optional;

public interface NodeHistory extends NodeHistoryBase {

    Optional<SidechainBlock> getBlockById(String blockId);

    SidechainBlock getBestBlock();

    Optional<BoxTransaction<Proposition, Box<Proposition>>> searchTransactionInsideSidechainBlock(String transactionId, String blockId);

    Optional<BoxTransaction<Proposition, Box<Proposition>>> searchTransactionInsideBlockchain(String transactionId);
}

