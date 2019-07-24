package com.horizen.node;

import com.horizen.block.SidechainBlock;

import java.util.List;
import java.util.Optional;

public interface NodeHistory {

    Optional<SidechainBlock> getBlockById(byte[] blockId);

    List<byte[]> getLastBlockids(SidechainBlock startBlock, int count);

    SidechainBlock getBestBlock();

    byte[] getBlockIdByHeight(int height);

    int getCurrentHeight();

}
