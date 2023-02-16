package com.horizen.account.api.rpc.types;

import com.fasterxml.jackson.annotation.JsonView;
import com.horizen.account.receipt.EthereumReceipt;
import com.horizen.account.transaction.EthereumTransaction;
import com.horizen.evm.utils.Address;
import com.horizen.evm.utils.Hash;
import com.horizen.serialization.Views;
import com.horizen.utils.BytesUtils;

import java.math.BigInteger;
import java.util.Objects;

@JsonView(Views.Default.class)
public class EthereumTransactionView {
    public final Hash blockHash;
    public final BigInteger blockNumber;
    public final Address from;
    public final Hash hash;
    public final BigInteger transactionIndex;
    public final BigInteger type;
    public final BigInteger nonce;
    public final Address to;
    public final BigInteger gas;
    public final BigInteger value;
    public final byte[] input;
    public final BigInteger maxPriorityFeePerGas;
    public final BigInteger maxFeePerGas;
    public final BigInteger gasPrice;
    // as we don't support transactions with accessList we don't include it here
    // note: accessList should be either the value used in the TX or empty-array []
    // if the transaction type does not support it, it should not be present at all - don't serialize as "null"
//    public final ArrayList<Object> accessList;
    public final BigInteger chainId;
    public final byte[] v;
    public final byte[] r;
    public final byte[] s;

    public EthereumTransactionView(EthereumTransaction tx, EthereumReceipt receipt, BigInteger baseFee) {
        assert Objects.equals(BytesUtils.toHexString(receipt.transactionHash()), tx.id());
        type = BigInteger.valueOf(tx.version());
        nonce = tx.getNonce();
        to = tx.getToAddress();
        gas = tx.getGasLimit();
        value = tx.getValue();
        input = tx.getData();
        gasPrice = tx.getEffectiveGasPrice(baseFee);
        if (tx.isEIP1559()) {
            maxPriorityFeePerGas = tx.getMaxPriorityFeePerGas();
            maxFeePerGas = tx.getMaxFeePerGas();
            // calculate effective gas price
        } else {
            maxPriorityFeePerGas = null;
            maxFeePerGas = null;
        }
        chainId = tx.getChainId() == null ? null : BigInteger.valueOf(tx.getChainId());
        var signature = tx.getSignature();
        if (signature == null) {
            v = null;
            r = null;
            s = null;
        } else {
            v = signature.getV();
            r = signature.getR();
            s = signature.getS();
        }
        blockHash = receipt.blockHash() != null ? new Hash(receipt.blockHash()) : null;
        blockNumber = BigInteger.valueOf(receipt.blockNumber());
        from = tx.getFromAddress();
        hash = new Hash(receipt.transactionHash());
        transactionIndex = BigInteger.valueOf(receipt.transactionIndex());
    }
}

