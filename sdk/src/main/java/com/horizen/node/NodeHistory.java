package com.horizen.node;

import com.horizen.block.MainchainBlockReference;
import com.horizen.block.MainchainHeader;
import com.horizen.block.SidechainBlock;
import com.horizen.box.Box;
import com.horizen.chain.FeePaymentsInfo;
import com.horizen.chain.MainchainHeaderInfo;
import com.horizen.chain.SidechainBlockInfo;
import com.horizen.node.util.MainchainBlockReferenceInfo;
import com.horizen.proposition.Proposition;
import com.horizen.transaction.BoxTransaction;

import java.util.List;
import java.util.Optional;

public interface NodeHistory {
    Optional<SidechainBlock> getBlockById(String blockId);

    Optional<SidechainBlockInfo> getBlockInfoById(String blockId);

    boolean isInActiveChain(String blockId);

    boolean isReindexing();

    List<String> getLastBlockIds(int count);

    SidechainBlock getBestBlock();

    Optional<String> getBlockIdByHeight(int height);

    Optional<Integer> getBlockHeightById(String id);

    int getCurrentHeight();

    Optional<FeePaymentsInfo> getFeePaymentsInfo(String blockId);

    Optional<Integer> getBlockHeight(String blockId);

    Optional<BoxTransaction<Proposition, Box<Proposition>>> searchTransactionInsideSidechainBlock(String transactionId, String blockId);

    Optional<BoxTransaction<Proposition, Box<Proposition>>> searchTransactionInsideBlockchain(String transactionId);

    int getMainchainCreationBlockHeight();

    Optional<MainchainBlockReferenceInfo> getBestMainchainBlockReferenceInfo();

    Optional<MainchainBlockReferenceInfo> getMainchainBlockReferenceInfoByHash(byte[] mainchainBlockReferenceHash);

    Optional<MainchainBlockReferenceInfo> getMainchainBlockReferenceInfoByMainchainBlockHeight(int height);

    Optional<MainchainBlockReference> getMainchainBlockReferenceByHash(byte[] mainchainBlockReferenceHash);

    Optional<MainchainHeader> getMainchainHeaderByHash(byte[] mainchainHeaderHash);

    Optional<MainchainHeaderInfo> getMainchainHeaderInfoByHash(byte[] mainchainHeaderHash);
}
