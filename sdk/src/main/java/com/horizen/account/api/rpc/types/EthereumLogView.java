package com.horizen.account.api.rpc.types;

import com.fasterxml.jackson.annotation.JsonView;
import com.horizen.account.receipt.EthereumReceipt;
import com.horizen.evm.interop.EvmLog;
import com.horizen.evm.utils.Address;
import com.horizen.evm.utils.Hash;
import com.horizen.serialization.Views;
import org.web3j.utils.Numeric;

import java.math.BigInteger;

@JsonView(Views.Default.class)
public class EthereumLogView {
    public final Address address;
    public final Hash[] topics;
    public final String data;
    public final Hash blockHash;
    public final String blockNumber;
    public final Hash transactionHash;
    public final String transactionIndex;
    public final String logIndex;
    public final boolean removed;

    public EthereumLogView(EthereumReceipt receipt, EvmLog log, int logIndex) {
        this.address = log.address;
        this.topics = log.topics;
        this.data = Numeric.toHexString(log.data);
        this.blockHash = new Hash(receipt.blockHash());
        this.blockNumber = Numeric.toHexStringWithPrefix(BigInteger.valueOf(receipt.blockNumber()));
        this.transactionHash = new Hash(receipt.transactionHash());
        this.transactionIndex = Numeric.toHexStringWithPrefix(BigInteger.valueOf(receipt.transactionIndex()));
        this.logIndex = Numeric.toHexStringWithPrefix(BigInteger.valueOf(logIndex));
        this.removed = false;
    }
}
