package com.horizen.account.state;

import com.horizen.account.block.AccountBlockHeader;

import java.math.BigInteger;

public class BlockContext {
    public final byte[] forgerAddress;
    public final long timestamp;
    public final BigInteger baseFee;
    public final long blockGasLimit;
    public final int blockNumber;
    public final int consensusEpochNumber;
    public final int withdrawalEpochNumber;

    public BlockContext(
            byte[] forgerAddress,
            long timestamp,
            long baseFee,
            long blockGasLimit,
            int blockNumber,
            int consensusEpochNumber,
            int withdrawalEpochNumber
    ) {
        this.forgerAddress = forgerAddress;
        this.timestamp = timestamp;
        this.baseFee = BigInteger.valueOf(baseFee);
        this.blockGasLimit = blockGasLimit;
        this.blockNumber = blockNumber;
        this.consensusEpochNumber = consensusEpochNumber;
        this.withdrawalEpochNumber = withdrawalEpochNumber;
    }

    public BlockContext(
            AccountBlockHeader blockHeader,
            int blockNumber,
            int consensusEpochNumber,
            int withdrawalEpochNumber
    ) {
        this(
                blockHeader.forgerAddress().address(),
                blockHeader.timestamp(),
                blockHeader.baseFee(),
                blockHeader.gasLimit(),
                blockNumber,
                consensusEpochNumber,
                withdrawalEpochNumber
        );
    }
}
