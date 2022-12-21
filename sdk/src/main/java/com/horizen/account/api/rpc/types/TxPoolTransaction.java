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
    public final String blockHash;         // fixed default value
    public final String blockNumber;       // always null
    public final String from;
    public final String gas;
    public final String gasPrice;
    public final String hash;              // transaction modifier id
    public final String input;             // transaction data
    public final String nonce;
    public final String to;
    public final String transactionIndex;  // always null
    public final String value;

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
}
