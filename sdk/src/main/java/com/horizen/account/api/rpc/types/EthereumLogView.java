package com.horizen.account.api.rpc.types;

import com.fasterxml.jackson.annotation.JsonView;
import com.horizen.account.receipt.EthereumReceipt;
import com.horizen.evm.interop.EvmLog;
import com.horizen.evm.utils.Hash;
import com.horizen.serialization.Views;
import org.web3j.utils.Numeric;

import java.math.BigInteger;
import java.util.Arrays;

@JsonView(Views.Default.class)
public class EthereumLogView {
    public final String address;
    public final String[] topics;
    public final String data;
    public final String blockHash;
    public final String blockNumber;
    public final String transactionHash;
    public final String transactionIndex;
    public final String logIndex;
    public final boolean removed;

    public EthereumLogView(EthereumReceipt receipt, EvmLog log, int logIndex) {
        this.address = log.getAddress().toString();
        this.topics = Arrays.stream(log.getTopics()).map(Hash::toBytes).map(Numeric::toHexString).toArray(String[]::new);
        this.data = Numeric.toHexString(log.getData());
        this.blockHash = Numeric.toHexString(receipt.blockHash());
        this.blockNumber = Numeric.toHexStringWithPrefix(BigInteger.valueOf(receipt.blockNumber()));
        this.transactionHash = Numeric.toHexString(receipt.transactionHash());
        this.transactionIndex = Numeric.toHexStringWithPrefix(BigInteger.valueOf(receipt.transactionIndex()));
        this.logIndex = Numeric.toHexStringWithPrefix(BigInteger.valueOf(logIndex));
        this.removed = false;
    }
}
