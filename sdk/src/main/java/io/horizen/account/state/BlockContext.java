package io.horizen.account.state;

import io.horizen.account.block.AccountBlockHeader;
import io.horizen.evm.Address;
import io.horizen.evm.Hash;
import io.horizen.evm.Tracer;

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
    private Tracer tracer;

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
        this.forgerAddress = blockHeader.forgerAddress().address();
        this.timestamp = blockHeader.timestamp();
        this.baseFee = blockHeader.baseFee();
        this.blockGasLimit = blockHeader.gasLimit();
        this.blockNumber = blockNumber;
        this.consensusEpochNumber = consensusEpochNumber;
        this.withdrawalEpochNumber = withdrawalEpochNumber;
        this.chainID = chainID;
        this.blockHashProvider = blockHashProvider;
        this.random = new Hash(blockHeader.vrfOutput().bytes());
    }

    public Tracer getTracer() {
        return this.tracer;
    }

    public void setTracer(Tracer tracer) {
        this.tracer = tracer;
    }

    public void removeTracer() {
        this.tracer = null;
    }
}
