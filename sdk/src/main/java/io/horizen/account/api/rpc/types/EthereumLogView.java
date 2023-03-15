package io.horizen.account.api.rpc.types;

import io.horizen.account.state.receipt.EthereumConsensusDataLog;
import io.horizen.account.state.receipt.EthereumReceipt;
import io.horizen.evm.Address;
import io.horizen.evm.Hash;

import java.math.BigInteger;

public class EthereumLogView {
    public final Address address;
    public final Hash[] topics;
    public final byte[] data;
    public final Hash blockHash;
    public final BigInteger blockNumber;
    public final Hash transactionHash;
    public final BigInteger transactionIndex;
    public final BigInteger logIndex;
    public boolean removed;

    public EthereumLogView(EthereumReceipt receipt, EthereumConsensusDataLog log, int logIndex) {
        this.address = log.address();
        this.topics = log.topics();
        this.data = log.data();
        this.blockHash = new Hash(receipt.blockHash());
        this.blockNumber = BigInteger.valueOf(receipt.blockNumber());
        this.transactionHash = new Hash(receipt.transactionHash());
        this.transactionIndex = BigInteger.valueOf(receipt.transactionIndex());
        this.logIndex = BigInteger.valueOf(logIndex);
        this.removed = false;
    }

    public void updateRemoved(boolean removed) {
        this.removed = removed;
    }
}
