package com.horizen.account.state;

import com.horizen.account.block.AccountBlockHeader;
import com.horizen.evm.interop.EvmResult;
import com.horizen.evm.interop.TraceOptions;
import com.horizen.evm.utils.Address;

import java.math.BigInteger;

public class BlockContext {
    public final Address forgerAddress;
    public final long timestamp;
    public final BigInteger baseFee;
    public final BigInteger blockGasLimit;
    public final int blockNumber;
    public final int consensusEpochNumber;
    public final int withdrawalEpochNumber;
    private TraceOptions traceOptions;
    public final long chainID;
    private EvmResult evmResult;

    public BlockContext(
        Address forgerAddress,
        long timestamp,
        BigInteger baseFee,
        BigInteger blockGasLimit,
        int blockNumber,
        int consensusEpochNumber,
        int withdrawalEpochNumber,
        long chainID
    ) {
        this.forgerAddress = forgerAddress;
        this.timestamp = timestamp;
        this.baseFee = baseFee;
        this.blockGasLimit = blockGasLimit;
        this.blockNumber = blockNumber;
        this.consensusEpochNumber = consensusEpochNumber;
        this.withdrawalEpochNumber = withdrawalEpochNumber;
        this.chainID = chainID;
    }

    public BlockContext(
        AccountBlockHeader blockHeader,
        int blockNumber,
        int consensusEpochNumber,
        int withdrawalEpochNumber,
        long chainID
    ) {
        this(
            blockHeader.forgerAddress().address(),
            blockHeader.timestamp(),
            blockHeader.baseFee(),
            blockHeader.gasLimit(),
            blockNumber,
            consensusEpochNumber,
            withdrawalEpochNumber,
            chainID
        );
    }

    public TraceOptions getTraceParams() {
        return this.traceOptions;
    }

    public void setTraceParams(TraceOptions tracer) {
        this.traceOptions = tracer;
    }

    public EvmResult getEvmResult() {
        return evmResult;
    }

    public void setEvmResult(EvmResult evmResult) {
        this.evmResult = evmResult;
    }
}
