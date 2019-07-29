package com.horizen.node;

import com.horizen.block.SidechainBlock;

import java.util.List;
import java.util.Optional;

public interface NodeHistory {
    Optional<SidechainBlock> getBlockById(String blockId);

    List<String> getLastBlockIds(SidechainBlock startBlock, int count);

    SidechainBlock getBestBlock();

    Optional<String> getBlockIdByHeight(int height);

    int getCurrentHeight();
}
