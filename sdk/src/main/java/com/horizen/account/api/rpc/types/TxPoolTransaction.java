package com.horizen.account.api.rpc.types;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonView;
import com.horizen.serialization.Views;
import org.web3j.utils.Numeric;

import java.math.BigInteger;

@JsonView(Views.Default.class)
@JsonInclude()
public class TxPoolTransaction {

    private final static String DEFAULT_BLOCK_HASH = "0x0000000000000000000000000000000000000000000000000000000000000000";
    private final String blockHash;         // fixed default value
    private final String blockNumber;       // always null
    private final String from;
    private final String gas;
    private final String gasPrice;
    private final String hash;              // transaction modifier id
    private final String input;             // transaction data
    private final String nonce;
    private final String to;
    private final String transactionIndex;  // always null
    private final String value;

    public TxPoolTransaction(byte[] from, BigInteger gas, BigInteger gasPrice, byte[] hash,
                             byte[] input, BigInteger nonce, byte[] to, BigInteger value) {
        this.blockHash = DEFAULT_BLOCK_HASH;
        this.blockNumber = null;
        this.from = (from!=null) ? Numeric.toHexString(from) : null;;
        this.gas = Numeric.encodeQuantity(gas);
        this.gasPrice = Numeric.encodeQuantity(gasPrice);
        this.hash = (hash!=null) ? Numeric.toHexString(hash) : null;
        this.input = (input!=null) ? Numeric.toHexString(input) : null;
        this.nonce = Numeric.encodeQuantity(nonce);
        this.to = (to!=null) ? Numeric.toHexString(to) : null;;
        this.transactionIndex = null;
        this.value = Numeric.encodeQuantity(value);
    }

    public String getBlockHash() {
        return blockHash;
    }

    public String getBlockNumber() {
        return blockNumber;
    }

    public String getFrom() {
        return from;
    }

    public String getGas() {
        return gas;
    }

    public String getGasPrice() {
        return gasPrice;
    }

    public String getHash() {
        return hash;
    }

    public String getInput() {
        return input;
    }

    public String getNonce() {
        return nonce;
    }

    public String getTo() {
        return to;
    }

    public String getTransactionIndex() {
        return transactionIndex;
    }

    public String getValue() {
        return value;
    }
}
