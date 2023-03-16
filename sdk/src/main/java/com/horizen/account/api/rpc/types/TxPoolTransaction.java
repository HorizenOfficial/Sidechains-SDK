package com.horizen.account.api.rpc.types;

import com.horizen.account.transaction.EthereumTransaction;
import io.horizen.evm.Address;
import io.horizen.evm.Hash;

import java.math.BigInteger;

public class TxPoolTransaction {
    // fixed default values
    public final Hash blockHash = Hash.ZERO;
    public final BigInteger blockNumber = null;
    public final BigInteger transactionIndex = null;

    public final Hash hash;
    public final BigInteger nonce;
    public final Address from;
    public final Address to;
    public final BigInteger value;
    public final byte[] input;
    public final BigInteger gas;
    public final BigInteger gasPrice;

    public TxPoolTransaction(EthereumTransaction tx) {
        this.hash = new Hash("0x" + tx.id());
        this.nonce = tx.getNonce();
        this.from = tx.getFromAddress();
        this.to = tx.getToAddress();
        this.value = tx.getValue();
        this.input = tx.getData();
        this.gas = tx.getGasLimit();
        this.gasPrice = tx.getGasPrice();
    }
}
