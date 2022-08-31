package com.horizen.account.state;

public class BlockContext {
    public final byte[] forgerAddress;
    public final long timestamp;
    public final long baseFee;
    public final long blockGasLimit;
    public final long blockNumber;
    public final long consensusEpochNumber;
    public final long withdrawalEpochNumber;

    public BlockContext(
            byte[] forgerAddress,
            long timestamp,
            long baseFee,
            long blockGasLimit,
            long blockNumber,
            long consensusEpochNumber,
            long withdrawalEpochNumber
    ) {
        this.forgerAddress = forgerAddress;
        this.timestamp = timestamp;
        this.baseFee = baseFee;
        this.blockGasLimit = blockGasLimit;
        this.blockNumber = blockNumber;
        this.consensusEpochNumber = consensusEpochNumber;
        this.withdrawalEpochNumber = withdrawalEpochNumber;
    }
}
