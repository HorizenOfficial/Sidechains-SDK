package com.horizen.node;

import com.horizen.block.MainchainBlockReference;
import com.horizen.block.SidechainBlock;
import com.horizen.node.util.MainchainBlockReferenceInfo;
import com.horizen.transaction.Transaction;
import scala.util.Try;

import java.util.List;
import java.util.Optional;

public interface NodeHistory {
    Optional<SidechainBlock> getBlockById(String blockId);

    List<String> getLastBlockIds(String startBlockId, int count);

    SidechainBlock getBestBlock();

    Optional<String> getBlockIdByHeight(int height);

    int getCurrentHeight();

    Optional<Transaction> searchTransactionInsideSidechainBlock(String transactionId, String blockId);

    Optional<Transaction> searchTransactionInsideBlockchain(String transactionId);

    MainchainBlockReferenceInfo getBestMainchainBlockReferenceInfo();

    MainchainBlockReference getMainchainBlockReferenceByHash(byte[] mainchainBlockReferenceHash);

    int getHeightOfMainchainBlock(byte[] mcBlockReferenceHash);

    Optional<SidechainBlock> getSidechainBlockByMainchainBlockReferenceHash(byte[] mcBlockReferenceHash);

    Try<MainchainBlockReference> createMainchainBlockReference(byte[] mainchainBlockData);
}
