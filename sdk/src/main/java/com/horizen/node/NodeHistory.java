package com.horizen.node;

import com.horizen.block.MainchainBlockReference;
import com.horizen.block.MainchainHeader;
import com.horizen.block.SidechainBlock;
import com.horizen.box.Box;
import com.horizen.chain.FeePaymentsInfo;
import com.horizen.chain.MainchainHeaderInfo;
import com.horizen.node.util.MainchainBlockReferenceInfo;
import com.horizen.proposition.Proposition;
import com.horizen.transaction.BoxTransaction;

import java.util.List;
import java.util.Optional;

public interface NodeHistory extends NodeHistoryBase {

    Optional<SidechainBlock> getBlockById(String blockId);

    SidechainBlock getBestBlock();

    Optional<BoxTransaction<Proposition, Box<Proposition>>> searchTransactionInsideSidechainBlock(String transactionId, String blockId);

    Optional<BoxTransaction<Proposition, Box<Proposition>>> searchTransactionInsideBlockchain(String transactionId);
}

