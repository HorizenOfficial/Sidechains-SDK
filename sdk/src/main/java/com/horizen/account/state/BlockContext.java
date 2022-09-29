package com.horizen.account.state;

import com.horizen.account.block.AccountBlockHeader;
import com.horizen.evm.interop.EvmContext;
import com.horizen.evm.interop.EvmResult;
import com.horizen.evm.interop.TraceParams;

import java.math.BigInteger;

public class BlockContext {
    public final byte[] forgerAddress;
    public final long timestamp;
    public final BigInteger baseFee;
    public final long blockGasLimit;
    public final int blockNumber;
    public final int consensusEpochNumber;
    public final int withdrawalEpochNumber;
    private TraceParams traceParams;
    private EvmResult evmResult;

    public BlockContext(
            byte[] forgerAddress,
            long timestamp,
            BigInteger baseFee,
            long blockGasLimit,
            int blockNumber,
            int consensusEpochNumber,
            int withdrawalEpochNumber
    ) {
        this.forgerAddress = forgerAddress;
        this.timestamp = timestamp;
        this.baseFee = baseFee;
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

    public TraceParams getTraceParams() {
        return this.traceParams;
    }

    public void setTraceParams(TraceParams tracer) {
        this.traceParams = tracer;
    }

    public EvmResult getEvmResult() {
        return evmResult;
    }

    public void setEvmResult(EvmResult evmResult) {
        this.evmResult = evmResult;
    }

}
