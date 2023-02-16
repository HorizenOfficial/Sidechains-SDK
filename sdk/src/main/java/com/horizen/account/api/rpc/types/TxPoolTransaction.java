package com.horizen.account.api.rpc.types;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonView;
import com.horizen.account.transaction.EthereumTransaction;
import com.horizen.evm.utils.Address;
import com.horizen.evm.utils.Hash;
import com.horizen.serialization.Views;

import java.math.BigInteger;

@JsonView(Views.Default.class)
@JsonInclude()
public class TxPoolTransaction {
    // fixed default value
    public final Hash blockHash = Hash.ZERO;
    // always null
    public final BigInteger blockNumber = null;
    // always null
    public final BigInteger transactionIndex = null;

    public final Hash hash;
    public final Address from;
    public final Address to;
    public final BigInteger nonce;
    public final BigInteger gas;
    public final BigInteger gasPrice;
    public final byte[] input;
    public final BigInteger value;

    public TxPoolTransaction(EthereumTransaction tx) {
        this.hash = new Hash("0x" + tx.id());
        this.from = tx.getFromAddress();
        this.to = tx.getToAddress();
        this.nonce = tx.getNonce();
        this.gas = tx.getGasLimit();
        this.gasPrice = tx.getGasPrice();
        this.input = tx.getData();
        this.value = tx.getValue();
    }
}
