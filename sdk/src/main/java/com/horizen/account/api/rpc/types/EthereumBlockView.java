package com.horizen.account.api.rpc.types;

import com.fasterxml.jackson.annotation.JsonView;
import com.horizen.account.block.AccountBlock;
import com.horizen.account.transaction.EthereumTransaction;
import com.horizen.serialization.Views;
import com.horizen.utils.BytesUtils;
import org.web3j.crypto.Sign;
import org.web3j.utils.Bytes;
import org.web3j.utils.Numeric;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@JsonView(Views.Default.class)
public class EthereumBlockView {
    public final Long number;
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
    public List<String> uncles = new ArrayList<>();
    public List<String> sealFields = new ArrayList<>();
    public String baseFeePerGas;

    public EthereumBlockView(Long blockNumber, String hash, boolean hydratedTx, AccountBlock block) {
        var blockHeader = block.header();
        this.author = Numeric.toHexString(block.header().forgerAddress().address());
        this.number = blockNumber;
        this.hash = hash;
        this.parentHash = Numeric.prependHexPrefix((String) block.parentId());
        this.nonce = "0x"; // no nonce
        this.sha3Uncles = "0x"; // no uncles
        this.logsBloom = Numeric.toHexString(block.header().logsBloom().getBloomFilter());
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

        var transactions = scala.collection.JavaConverters.seqAsJavaList(block.transactions());

        if (!hydratedTx) {
            this.transactions = transactions.stream().map(t -> Numeric.prependHexPrefix((String) t.id())).collect(Collectors.toList());
        } else {
            this.transactions = transactions.stream().map(t -> new EthereumTransactionView(null, new EthereumTransaction(BytesUtils.toHexString(t.getTo().bytes()), t.getNonce(), t.getGasPrice(), t.getGasLimit(), t.getValue(), BytesUtils.toHexString(t.getData()), (Sign.SignatureData) t.getSignature()), block.header().baseFee())).collect(Collectors.toList());
        }
    }
}

