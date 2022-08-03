package com.horizen.account.api.rpc.types;

import com.fasterxml.jackson.annotation.JsonView;
import com.horizen.account.receipt.EthereumReceipt;
import com.horizen.account.transaction.EthereumTransaction;
import com.horizen.account.utils.Account;
import com.horizen.serialization.Views;
import org.web3j.utils.Numeric;

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
    private String maxPriorityFeePerGas;
    private String maxFeePerGas;
    private String gasPrice;
    private ArrayList<Object> accessList;
    private String chainId;
    private final String v;
    private final String r;
    private final String s;

    public EthereumTransactionView(EthereumReceipt receipt, EthereumTransaction ethTx) {
        type = Numeric.prependHexPrefix((Integer.toHexString(ethTx.transactionTypeId())));
        nonce = Numeric.toHexStringWithPrefix(ethTx.getNonce());
        to = Numeric.cleanHexPrefix(ethTx.getToAddress()).length() != 2*Account.ADDRESS_SIZE ? null : ethTx.getToAddress();
        gas = Numeric.toHexStringWithPrefix(ethTx.getGasLimit());
        value = Numeric.toHexStringWithPrefix(ethTx.getValue());
        input = Numeric.toHexString(ethTx.getData());
        if (ethTx.isEIP1559()) {
            maxPriorityFeePerGas = Numeric.toHexStringWithPrefix(ethTx.getMaxPriorityFeePerGas());
            maxFeePerGas = Numeric.toHexStringWithPrefix(ethTx.getMaxFeePerGas());
        } else {
            gasPrice = Numeric.toHexStringWithPrefix(ethTx.getGasPrice());
        }
        if (ethTx.getChainId() != null) chainId = String.valueOf(ethTx.getChainId());
        v = (ethTx.getSignatureData() != null) ? Numeric.toHexString(ethTx.getSignatureData().getV()) : null;
        r = (ethTx.getSignatureData() != null) ? Numeric.toHexString(ethTx.getSignatureData().getR()) : null;
        s = (ethTx.getSignatureData() != null) ? Numeric.toHexString(ethTx.getSignatureData().getS()) : null;
        blockHash = Numeric.toHexString(receipt.blockHash());
        blockNumber = Numeric.prependHexPrefix(Integer.toHexString(receipt.blockNumber()));
        from = (ethTx.getFrom() != null) ? Numeric.toHexString(ethTx.getFrom().address()) : null;
        hash = Numeric.toHexString(receipt.transactionHash());
        transactionIndex = Numeric.prependHexPrefix(Integer.toHexString(receipt.transactionIndex()));
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

