package com.horizen.account.api.rpc.types;

import com.fasterxml.jackson.annotation.JsonView;
import com.horizen.account.receipt.EthereumReceipt;
import com.horizen.account.transaction.EthereumTransaction;
import com.horizen.account.utils.Account;
import com.horizen.serialization.Views;
import org.web3j.utils.Numeric;

import java.math.BigInteger;
import java.util.ArrayList;

@JsonView(Views.Default.class)
public class EthereumTransactionView {
    private final String blockHash;
    private final String blockNumber;
    private final String from;
    private final String hash;
    private final String transactionIndex;
    private final String type;
    private final String nonce;
    private final String to;
    private final String gas;
    private final String value;
    private final String input;
    private final String maxPriorityFeePerGas;
    private final String maxFeePerGas;
    private final String gasPrice;
    private final ArrayList<Object> accessList;
    private final String chainId;
    private final String v;
    private final String r;
    private final String s;

    public EthereumTransactionView(EthereumReceipt receipt, EthereumTransaction ethTx, BigInteger baseFee) {
        type = Numeric.prependHexPrefix((Integer.toHexString(ethTx.version())));
        nonce = Numeric.encodeQuantity(ethTx.getNonce());
        to = Numeric.cleanHexPrefix(ethTx.getToAddressString()).length() != 2 * Account.ADDRESS_SIZE ? null : ethTx.getToAddressString();
        gas = Numeric.encodeQuantity(ethTx.getGasLimit());
        value = Numeric.encodeQuantity(ethTx.getValue());
        input = Numeric.toHexString(ethTx.getData());
        gasPrice = Numeric.encodeQuantity(ethTx.getEffectiveGasPrice(baseFee));
        if (ethTx.isEIP1559()) {
            maxPriorityFeePerGas = Numeric.encodeQuantity(ethTx.getMaxPriorityFeePerGas());
            maxFeePerGas = Numeric.encodeQuantity(ethTx.getMaxFeePerGas());
            // calculate effective gas price
        } else {
            maxPriorityFeePerGas = null;
            maxFeePerGas = null;
        }
        chainId = ethTx.getChainId() == null ? null : Numeric.encodeQuantity(BigInteger.valueOf(ethTx.getChainId()));
        v = (ethTx.getSignature() != null) ? Numeric.toHexString(ethTx.getSignature().getV()) : null;
        r = (ethTx.getSignature() != null) ? Numeric.toHexString(ethTx.getSignature().getR()) : null;
        s = (ethTx.getSignature() != null) ? Numeric.toHexString(ethTx.getSignature().getS()) : null;
        blockHash = Numeric.toHexString(receipt.blockHash());
        blockNumber = Numeric.encodeQuantity(BigInteger.valueOf(receipt.blockNumber()));
        from = (ethTx.getFrom() != null) ? Numeric.toHexString(ethTx.getFrom().address()) : null;
        hash = Numeric.toHexString(receipt.transactionHash());
        transactionIndex = Numeric.encodeQuantity(BigInteger.valueOf(receipt.transactionIndex()));
        accessList = null;
    }

    public String getType() {
        return this.type;
    }

    public String getNonce() {
        return this.nonce;
    }

    public String getTo() {
        return this.to;
    }

    public String getGas() {
        return this.gas;
    }

    public String getValue() {
        return this.value;
    }

    public String getInput() {
        return this.input;
    }

    public String getMaxPriorityFeePerGas() {
        return this.maxPriorityFeePerGas;
    }

    public String getMaxFeePerGas() {
        return this.maxFeePerGas;
    }

    public String getGasPrice() {
        return this.gasPrice;
    }

    public ArrayList<Object> getAccessList() {
        return this.accessList;
    }

    public String getChainId() {
        return this.chainId;
    }

    public String getV() {
        return this.v;
    }

    public String getR() {
        return this.r;
    }

    public String getS() {
        return this.s;
    }

    public String getBlockHash() {
        return this.blockHash;
    }

    public String getBlockNumber() {
        return this.blockNumber;
    }

    public String getFrom() {
        return this.from;
    }

    public String getHash() {
        return this.hash;
    }

    public String getTransactionIndex() {
        return this.transactionIndex;
    }
}

