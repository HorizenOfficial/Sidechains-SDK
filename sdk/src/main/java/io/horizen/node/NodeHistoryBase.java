package io.horizen.node;

import io.horizen.block.*;
import io.horizen.chain.AbstractFeePaymentsInfo;
import io.horizen.chain.MainchainBlockReferenceInfo;
import io.horizen.chain.MainchainHeaderInfo;
import io.horizen.chain.SidechainBlockInfo;
import io.horizen.transaction.Transaction;

import java.util.List;
import java.util.Optional;

public interface NodeHistoryBase<
        TX extends Transaction,
        H extends SidechainBlockHeaderBase,
        PM extends SidechainBlockBase<TX,H>,
        FPI extends AbstractFeePaymentsInfo>  {

    Optional<PM> getBlockById(String blockId);

    Optional<SidechainBlockInfo> getBlockInfoById(String blockId);

    boolean isInActiveChain(String blockId);

    PM getBestBlock();

    List<String> getLastBlockIds(int count);

    Optional<String> getBlockIdByHeight(int height);

    Optional<Integer> getBlockHeightById(String id);

    int getCurrentHeight();

    Optional<FPI> getFeePaymentsInfo(String blockId);

    int getMainchainCreationBlockHeight();

    Optional<MainchainBlockReferenceInfo> getBestMainchainBlockReferenceInfo();

    Optional<MainchainBlockReferenceInfo> getMainchainBlockReferenceInfoByHash(byte[] mainchainBlockReferenceHash);

    Optional<MainchainBlockReferenceInfo> getMainchainBlockReferenceInfoByMainchainBlockHeight(int height);

    Optional<MainchainBlockReference> getMainchainBlockReferenceByHash(MainchainHeaderHash mainchainBlockReferenceHash);

    Optional<MainchainHeader> getMainchainHeaderByHash(MainchainHeaderHash mainchainHeaderHash);

    Optional<MainchainHeaderInfo> getMainchainHeaderInfoByHash(byte[] mainchainHeaderHash);

    Optional<TX> searchTransactionInsideSidechainBlock(String transactionId, String blockId);


}

