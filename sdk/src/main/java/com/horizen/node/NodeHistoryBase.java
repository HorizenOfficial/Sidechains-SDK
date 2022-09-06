package com.horizen.node;

import com.horizen.block.*;
import com.horizen.chain.AbstractFeePaymentsInfo;
import com.horizen.chain.MainchainHeaderInfo;
import com.horizen.node.util.MainchainBlockReferenceInfo;
import com.horizen.transaction.Transaction;

import java.util.List;
import java.util.Optional;

public interface NodeHistoryBase<
        TX extends Transaction,
        H extends SidechainBlockHeaderBase,
        PM extends SidechainBlockBase<TX,H>,
        FPI extends AbstractFeePaymentsInfo>  {

    Optional<PM> getBlockById(String blockId);

    PM getBestBlock();

    List<String> getLastBlockIds(int count);

    Optional<String> getBlockIdByHeight(int height);

    Optional<Integer> getBlockHeightById(String id);

    int getCurrentHeight();

    Optional<FPI> getFeePaymentsInfo(String blockId);

    Optional<Integer> getBlockHeight(String blockId);

    int getMainchainCreationBlockHeight();

    Optional<MainchainBlockReferenceInfo> getBestMainchainBlockReferenceInfo();

    Optional<MainchainBlockReferenceInfo> getMainchainBlockReferenceInfoByHash(byte[] mainchainBlockReferenceHash);

    Optional<MainchainBlockReferenceInfo> getMainchainBlockReferenceInfoByMainchainBlockHeight(int height);

    Optional<MainchainBlockReference> getMainchainBlockReferenceByHash(byte[] mainchainBlockReferenceHash);

    Optional<MainchainHeader> getMainchainHeaderByHash(byte[] mainchainHeaderHash);

    Optional<MainchainHeaderInfo> getMainchainHeaderInfoByHash(byte[] mainchainHeaderHash);
}

