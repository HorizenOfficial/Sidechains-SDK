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

    List<String> getLastBlockIds(SidechainBlock startBlock, int count);

    SidechainBlock getBestBlock();

    Optional<String> getBlockIdByHeight(int height);

    int getCurrentHeight();

    Optional<Transaction> searchTransactionInsideSidechainBlock(String transactionId, String blockId);

    Optional<Transaction> searchTransactionInsideBlockchain(String transactionId);

    Optional<MainchainBlockReferenceInfo> getBestMainchainBlockReferenceInfo();

    Optional<MainchainBlockReferenceInfo> getMainchainBlockReferenceInfoByHash(byte[] mainchainBlockReferenceHash);

    Optional<MainchainBlockReferenceInfo> getMainchainBlockReferenceInfoByMainchainBlockReferenceInfoHeight(int height);

    Optional<MainchainBlockReference> getMainchainBlockReferenceByHash(byte[] mainchainBlockReferenceHash);
}
