package com.horizen.account.api.rpc.types;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonView;
import com.horizen.account.block.AccountBlock;
import com.horizen.serialization.Views;
import org.web3j.utils.Numeric;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@JsonView(Views.Default.class)
public class EthereumBlockView {
    public final String number;
    public final String hash;
    public final String parentHash;
    public final String nonce;
    public final String sha3Uncles;
    public final String logsBloom;
    public final String transactionsRoot;
    public final String stateRoot;
    public final String receiptsRoot;
    public final String miner;
    public final String mixHash;
    public final String extraData;
    public final String size;
    public final String gasLimit;
    public final String gasUsed;
    public final String timestamp;
    public final List<?> transactions;
    public String author;
    public String difficulty;
    public String totalDifficulty;
    public List<String> uncles;
    public List<String> sealFields;
    public String baseFeePerGas;

    public EthereumBlockView(Long blockNumber, String hash, boolean hydratedTx, AccountBlock block, List<EthereumTransactionView> txList) {
        var blockHeader = block.header();
        this.author = Numeric.toHexString(block.header().forgerAddress().address());
        this.number = Numeric.encodeQuantity(BigInteger.valueOf(blockNumber));
        this.hash = hash;
        this.parentHash = Numeric.prependHexPrefix((String) block.parentId());
        // no nonce, but we explicity set it to all zeroes as some RPC clients are very strict (e.g. GETH)
        this.nonce = "0x0000000000000000";
        this.sha3Uncles = "0x"; // no uncles
        this.logsBloom = Numeric.toHexString(block.header().logsBloom().getBytes());
        this.transactionsRoot = Numeric.toHexString(block.header().sidechainTransactionsMerkleRootHash());
        this.stateRoot = Numeric.toHexString(block.header().stateRoot());
        this.receiptsRoot = Numeric.toHexString(block.header().receiptsRoot());
        this.miner = Numeric.toHexString(block.header().forgerAddress().address());
        this.mixHash = "0x";
        this.extraData = "0x";
        this.size = Numeric.prependHexPrefix(Integer.toHexString(block.header().bytes().length));
        this.gasLimit = Numeric.toHexStringWithPrefix(BigInteger.valueOf(blockHeader.gasLimit()));
        this.gasUsed = Numeric.toHexStringWithPrefix(BigInteger.valueOf(blockHeader.gasUsed()));
        this.timestamp = Numeric.prependHexPrefix(Long.toHexString(block.timestamp()));
        this.baseFeePerGas = Numeric.toHexStringWithPrefix(blockHeader.baseFee());
        this.difficulty = "0x0";
        this.totalDifficulty = "0x0";

        if (!hydratedTx) {
            var transactions = scala.collection.JavaConverters.seqAsJavaList(block.transactions());
            this.transactions = transactions.stream().map(t -> Numeric.prependHexPrefix((String) t.id())).collect(Collectors.toList());
        } else {
            this.transactions = txList;
        }
    }

    @JsonInclude
    public List<String> getUncles() {
        return new ArrayList<>();
    }
}

