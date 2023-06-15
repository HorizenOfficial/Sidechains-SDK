package io.horizen.account.api.rpc.types;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.horizen.account.state.receipt.EthereumReceipt;
import io.horizen.account.transaction.EthereumTransaction;
import io.horizen.evm.Address;
import io.horizen.evm.Hash;
import io.horizen.utils.BytesUtils;

import java.math.BigInteger;
import java.util.Objects;


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
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public final BigInteger maxPriorityFeePerGas;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public final BigInteger maxFeePerGas;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public final BigInteger chainId;
    public final BigInteger v;
    public final BigInteger r;
    public final BigInteger s;

    /**
     * Note for the correct JSON representation of the accessList property:
     * - legacy transactions do not support the "accessList" field at all: set to null to hide it from the JSON representation
     * - EIP1559 transactions support the "accessList" field, even though we don't: it should be set to an empty-array
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public final Object[] accessList;

    public EthereumTransactionView(EthereumTransaction tx, EthereumReceipt receipt, BigInteger baseFee) {

        if(receipt != null) assert Objects.equals(BytesUtils.toHexString(receipt.transactionHash()), tx.id());
        type = BigInteger.valueOf(tx.version());
        nonce = tx.getNonce();
        from = tx.getFromAddress();
        to = tx.getToAddress();
        value = tx.getValue();
        input = tx.getData();
        gas = tx.getGasLimit();
        // if a transaction is already confirmed in a block the gas price will be the effective gas price based on the base fee
        // otherwise for legacy transactions the gas price is the gasPrice and for the EIP1559 is the maxFeePerGas
        gasPrice = baseFee != null ? tx.getEffectiveGasPrice(baseFee) : tx.getGasPrice();
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
            v = null; r = null; s = null;
        } else {
            v = signature.getV();
            r = signature.getR();
            s = signature.getS();
        }

        if(receipt != null) {
            hash = new Hash(receipt.transactionHash());
            blockHash = receipt.blockHash() != null ? new Hash(receipt.blockHash()) : null;
            blockNumber = BigInteger.valueOf(receipt.blockNumber());
            transactionIndex = BigInteger.valueOf(receipt.transactionIndex());
        } else {
            hash = new Hash("0x" + tx.id());
            blockHash = null; blockNumber = null; transactionIndex = null;
        }

    }

    // constructor used for the pending transactions
    public EthereumTransactionView(EthereumTransaction tx) {
        this(tx, null, null);
    }

}

