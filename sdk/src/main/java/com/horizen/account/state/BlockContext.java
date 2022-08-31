package com.horizen.account.state;

import com.horizen.account.block.AccountBlockHeader;

import java.math.BigInteger;

public class BlockContext {
    public final byte[] forgerAddress;
    public final long timestamp;
    public final BigInteger baseFee;
    public final long blockGasLimit;
    public final int blockNumber;
    public final long consensusEpochNumber;
    public final long withdrawalEpochNumber;

    public BlockContext(
            byte[] forgerAddress,
            long timestamp,
            long baseFee,
            long blockGasLimit,
            int blockNumber,
            long consensusEpochNumber,
            long withdrawalEpochNumber
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
            long consensusEpochNumber,
            long withdrawalEpochNumber
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
