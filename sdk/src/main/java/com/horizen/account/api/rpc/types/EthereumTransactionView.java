package com.horizen.account.api.rpc.types;

import com.fasterxml.jackson.annotation.JsonInclude;
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
    // block data
    public final Hash blockHash;
    public final BigInteger blockNumber;
    public final BigInteger transactionIndex;

    // transaction data
    public final Hash hash;
    public final BigInteger type;
    public final BigInteger nonce;
    public final Address from;
    public final Address to;
    public final BigInteger value;
    public final byte[] input;
    public final BigInteger gas;
    public final BigInteger gasPrice;
    public final BigInteger maxPriorityFeePerGas;
    public final BigInteger maxFeePerGas;
    public final BigInteger chainId;
    public final byte[] v;
    public final byte[] r;
    public final byte[] s;

    /**
     * Note for the correct JSON representation of the accessList property:
     * - legacy transactions do not support the "accessList" field at all: set to null to hide it from the JSON representation
     * - EIP1559 transactions support the "accessList" field, even though we don't: it should be set to an empty-array
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public final Object[] accessList;

    public EthereumTransactionView(EthereumTransaction tx, EthereumReceipt receipt, BigInteger baseFee) {
        assert Objects.equals(BytesUtils.toHexString(receipt.transactionHash()), tx.id());
        type = BigInteger.valueOf(tx.version());
        nonce = tx.getNonce();
        from = tx.getFromAddress();
        to = tx.getToAddress();
        value = tx.getValue();
        input = tx.getData();
        gas = tx.getGasLimit();
        gasPrice = tx.getEffectiveGasPrice(baseFee);
        if (tx.isEIP1559()) {
            maxPriorityFeePerGas = tx.getMaxPriorityFeePerGas();
            maxFeePerGas = tx.getMaxFeePerGas();
            accessList = new Object[0];
        } else {
            maxPriorityFeePerGas = null;
            maxFeePerGas = null;
            accessList = null;
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
        hash = new Hash(receipt.transactionHash());
        blockHash = receipt.blockHash() != null ? new Hash(receipt.blockHash()) : null;
        blockNumber = BigInteger.valueOf(receipt.blockNumber());
        transactionIndex = BigInteger.valueOf(receipt.transactionIndex());
    }
}

