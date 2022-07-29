package com.horizen.account.api.rpc.types;

import com.fasterxml.jackson.annotation.JsonView;
import com.horizen.account.block.AccountBlock;
import com.horizen.serialization.Views;
import org.web3j.utils.Numeric;

import java.util.List;

@JsonView(Views.Default.class)
public class EthereumBlock {
    private final String number;
    private final String hash;
    private final String parentHash;
    private final String nonce;
    private final String sha3Uncles;
    private final String logsBloom;
    private final String transactionsRoot;
    private final String stateRoot;
    private final String receiptsRoot;
    private final String miner;
    private final String mixHash;
    private final String extraData;
    private final String size;
    private final String gasLimit;
    private final String gasUsed;
    private final String timestamp;
    private final List<?> transactions;
    private String author;
    private String difficulty;
    private String totalDifficulty;
    private List<String> uncles;
    private List<String> sealFields;
    private String baseFeePerGas;

    public EthereumBlock(String number, String hash, List<?> transactions, AccountBlock block) {
        this.number = number;
        this.hash = hash;
        this.parentHash = Numeric.prependHexPrefix((String) block.parentId());
        this.nonce = "0x0"; // no nonce
        this.sha3Uncles = "0x0"; // no uncles
        this.logsBloom = "0x0"; // not included in block now
        this.transactionsRoot = Numeric.toHexString(block.header().sidechainTransactionsMerkleRootHash());
        this.stateRoot = Numeric.toHexString(block.header().stateRoot());
        this.receiptsRoot = Numeric.toHexString(block.header().receiptsRoot());
        this.miner = Numeric.toHexString(block.header().forgerAddress().address());
        this.mixHash = "0x0";
        this.extraData = "0x0";
        this.size = Numeric.prependHexPrefix(Integer.toHexString(block.header().bytes().length));
        this.gasLimit = "0x5208";
        this.gasUsed = "0x1";
        this.timestamp = Numeric.prependHexPrefix(Long.toHexString(block.timestamp()));
        this.transactions = transactions;
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

    public List<?> getTransactions() {
        return this.transactions;
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

