package com.horizen.account.api.rpc.types;

import com.horizen.account.receipt.EthereumConsensusDataLog;
import com.horizen.account.receipt.EthereumReceipt;
import com.horizen.evm.utils.Address;
import com.horizen.evm.utils.Hash;

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
    public final boolean removed;

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
}
