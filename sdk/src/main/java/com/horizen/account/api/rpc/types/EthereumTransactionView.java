package com.horizen.account.api.rpc.types;

import com.fasterxml.jackson.annotation.JsonView;
import com.horizen.account.receipt.EthereumReceipt;
import com.horizen.account.transaction.EthereumTransaction;
import com.horizen.evm.utils.Address;
import com.horizen.evm.utils.Hash;
import com.horizen.serialization.Views;
import com.horizen.utils.BytesUtils;
import org.web3j.utils.Numeric;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Objects;

@JsonView(Views.Default.class)
public class EthereumTransactionView {
    public final Hash blockHash;
    public final String blockNumber;
    public final Address from;
    public final Hash hash;
    public final String transactionIndex;
    public final String type;
    public final String nonce;
    public final Address to;
    public final String gas;
    public final String value;
    public final String input;
    public final String maxPriorityFeePerGas;
    public final String maxFeePerGas;
    public final String gasPrice;
    public final ArrayList<Object> accessList;
    public final String chainId;
    public final String v;
    public final String r;
    public final String s;

    public EthereumTransactionView(EthereumTransaction tx, EthereumReceipt receipt, BigInteger baseFee) {
        assert Objects.equals(BytesUtils.toHexString(receipt.transactionHash()), tx.id());
        type = Numeric.encodeQuantity(BigInteger.valueOf(tx.version()));
        nonce = Numeric.encodeQuantity(tx.getNonce());
        to = tx.getToAddress();
        gas = Numeric.encodeQuantity(tx.getGasLimit());
        value = Numeric.encodeQuantity(tx.getValue());
        input = Numeric.toHexString(tx.getData());
        gasPrice = Numeric.encodeQuantity(tx.getEffectiveGasPrice(baseFee));
        if (tx.isEIP1559()) {
            maxPriorityFeePerGas = Numeric.encodeQuantity(tx.getMaxPriorityFeePerGas());
            maxFeePerGas = Numeric.encodeQuantity(tx.getMaxFeePerGas());
            // calculate effective gas price
        } else {
            maxPriorityFeePerGas = null;
            maxFeePerGas = null;
        }
        chainId = tx.getChainId() == null ? null : Numeric.encodeQuantity(BigInteger.valueOf(tx.getChainId()));
        var signature = tx.getSignature();
        if (signature == null) {
            v = null;
            r = null;
            s = null;
        } else {
            v = Numeric.toHexString(signature.getV());
            r = Numeric.toHexString(signature.getR());
            s = Numeric.toHexString(signature.getS());
        }
        blockHash = receipt.blockHash() != null ? new Hash(receipt.blockHash()) : null;
        blockNumber = Numeric.encodeQuantity(BigInteger.valueOf(receipt.blockNumber()));
        from = tx.getFromAddress();
        hash = new Hash(receipt.transactionHash());
        transactionIndex = Numeric.encodeQuantity(BigInteger.valueOf(receipt.transactionIndex()));
        accessList = null;
    }
}

