package com.horizen.account.state;

import com.horizen.account.block.AccountBlockHeader;
import com.horizen.evm.results.EvmResult;
import com.horizen.evm.TraceOptions;
import com.horizen.evm.Address;
import com.horizen.evm.Hash;

import java.math.BigInteger;

public class BlockContext {
    public final Address forgerAddress;
    public final long timestamp;
    public final BigInteger baseFee;
    public final BigInteger blockGasLimit;
    public final int blockNumber;
    public final int consensusEpochNumber;
    public final int withdrawalEpochNumber;
    public final long chainID;
    public final HistoryBlockHashProvider blockHashProvider;
    public final Hash random;
    private TraceOptions traceOptions;

    private EvmResult evmResult;

    public BlockContext(
        Address forgerAddress,
        long timestamp,
        BigInteger baseFee,
        BigInteger blockGasLimit,
        int blockNumber,
        int consensusEpochNumber,
        int withdrawalEpochNumber,
        long chainID,
        HistoryBlockHashProvider blockHashProvider,
        Hash random
    ) {
        this.forgerAddress = forgerAddress;
        this.timestamp = timestamp;
        this.baseFee = baseFee;
        this.blockGasLimit = blockGasLimit;
        this.blockNumber = blockNumber;
        this.consensusEpochNumber = consensusEpochNumber;
        this.withdrawalEpochNumber = withdrawalEpochNumber;
        this.chainID = chainID;
        this.blockHashProvider = blockHashProvider;
        this.random = random;
    }

    public BlockContext(
        AccountBlockHeader blockHeader,
        int blockNumber,
        int consensusEpochNumber,
        int withdrawalEpochNumber,
        long chainID,
        HistoryBlockHashProvider blockHashProvider
    ) {
        this(
            blockHeader.forgerAddress().address(),
            blockHeader.timestamp(),
            blockHeader.baseFee(),
            blockHeader.gasLimit(),
            blockNumber,
            consensusEpochNumber,
            withdrawalEpochNumber,
            chainID,
            blockHashProvider,
            new Hash(blockHeader.vrfOutput().bytes())
        );
    }

    public TraceOptions getTraceOptions() {
        return this.traceOptions;
    }

    public void setTraceOptions(TraceOptions traceOptions) {
        this.traceOptions = traceOptions;
    }

    public EvmResult getEvmResult() {
        return evmResult;
    }

    public void setEvmResult(EvmResult evmResult) {
        this.evmResult = evmResult;
    }
}
