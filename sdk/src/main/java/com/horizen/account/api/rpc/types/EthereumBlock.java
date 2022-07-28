package com.horizen.account.api.rpc.types;

import com.fasterxml.jackson.annotation.JsonView;
import com.horizen.account.block.AccountBlock;
import com.horizen.serialization.Views;
import org.web3j.utils.Numeric;

import java.util.List;

@JsonView(Views.Default.class)
public class EthereumBlock {
    private String number;
    private String hash;
    private String parentHash;
    private String nonce;
    private String sha3Uncles;
    private String logsBloom;
    private String transactionsRoot;
    private String stateRoot;
    private String receiptsRoot;
    private String author;
    private String miner;
    private String mixHash;
    private String difficulty;
    private String totalDifficulty;
    private String extraData;
    private String size;
    private String gasLimit;
    private String gasUsed;
    private String timestamp;
    private List<String> transactions;
    private List<EthereumTransactionView> transactionViews;
    private List<String> uncles;
    private List<String> sealFields;
    private String baseFeePerGas;

    public EthereumBlock() {
    }

    public EthereumBlock(String number, String hash, List<String> transactions, List<EthereumTransactionView> transactionViews, Boolean hydrated, AccountBlock block) {
        this.number = number;
        this.hash = hash;
        this.parentHash = "0x0";
        this.nonce = "0x0"; // no nonce
        this.sha3Uncles = "0x0"; // no uncles
        this.logsBloom = "0x0"; // not included in block now
        this.transactionsRoot = "0x0";
        this.stateRoot = Numeric.toHexString(block.header().stateRoot());
        this.receiptsRoot = Numeric.toHexString(block.header().receiptsRoot());
        this.miner = "0x0";
        this.mixHash = "0x0";
        this.extraData = "0x0";
        this.size = String.valueOf(block.header().bytes().length);
        this.gasLimit = "0x5208";
        this.gasUsed = "0x1";
        this.timestamp = String.valueOf(block.timestamp());
        if (hydrated) this.transactionViews = transactionViews;
        else this.transactions = transactions;
    }

    public String getNumber() {
        return this.number;
    }

    public String getHash() {
        return this.hash;
    }

    public String getParentHash() {
        return this.parentHash;
    }

    public String getNonce() {
        return this.nonce;
    }

    public String getSha3Uncles() {
        return this.sha3Uncles;
    }

    public String getLogsBloom() {
        return this.logsBloom;
    }

    public String getTransactionsRoot() {
        return this.transactionsRoot;
    }

    public String getStateRoot() {
        return this.stateRoot;
    }

    public String getReceiptsRoot() {
        return this.receiptsRoot;
    }

    public String getAuthor() {
        return this.author;
    }

    public String getMiner() {
        return this.miner;
    }

    public String getMixHash() {
        return this.mixHash;
    }

    public String getDifficulty() {
        return this.difficulty;
    }

    public String getTotalDifficulty() {
        return this.totalDifficulty;
    }

    public String getExtraData() {
        return this.extraData;
    }

    public String getSize() {
        return this.size;
    }

    public String getGasLimit() {
        return this.gasLimit;
    }

    public String getGasUsed() {
        return this.gasUsed;
    }

    public String getTimestamp() {
        return this.timestamp;
    }

    public List<String> getTransactions() {
        return this.transactions;
    }

    public List<EthereumTransactionView> getTransactionViews() {
        return this.transactionViews;
    }

    public List<String> getUncles() {
        return this.uncles;
    }

    public List<String> getSealFields() {
        return this.sealFields;
    }

    public String getBaseFeePerGas() {
        return this.baseFeePerGas;
    }
}

