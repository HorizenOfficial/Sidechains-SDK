package com.horizen.account.api.rpc.types;

import com.fasterxml.jackson.annotation.JsonView;
import com.horizen.account.block.AccountBlock;
import com.horizen.account.receipt.EthereumReceipt;
import com.horizen.account.utils.AccountBlockUtil;
import com.horizen.evm.utils.Address;
import com.horizen.evm.utils.Hash;
import com.horizen.serialization.Views;
import org.web3j.utils.Numeric;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@JsonView(Views.Default.class)
public class EthereumBlockView {
    public final String number;
    public final Hash hash;
    public final Hash parentHash;
    public final String logsBloom;
    public final Hash transactionsRoot;
    public final Hash stateRoot;
    public final Hash receiptsRoot;
    public final Address miner;
    public final String size;
    public final String gasLimit;
    public final String gasUsed;
    public final String timestamp;
    public final List<?> transactions;
    public final Address author;
    public final String baseFeePerGas;
    public final List<String> uncles = new ArrayList<>();
    public final List<String> sealFields = new ArrayList<>();

    // we use PoS and do not have difficulty, Ethereum mainnet also set this to zero since the switch to PoS
    public final String difficulty = "0x0";
    // total difficulty should be the sum of the difficulty of all previous blocks, since we never had PoW this is zero
    // on Ethereum mainnet it is constant since "The Merge": 58_750_000_000_000_000_000_000
    public final String totalDifficulty = "0x0";
    // no PoW, no nonce, but we explicity set it to all zeroes as some RPC clients are very strict here (e.g. GETH)
    public final String nonce = "0x0000000000000000";
    // we do not have uncles
    public final String sha3Uncles = "0x";
    // we do not have extraData in the block
    public final String extraData = "0x";
    // currently we do not use the mixHash, but we will set it to a VRF random number in the future to support the
    // PREVRANDAO EVM-opcode, just like Ethereum does since The Merge
    public final String mixHash = "0x";

    private EthereumBlockView(Long blockNumber, Hash blockHash, AccountBlock block, List<?> txs) {
        var header = block.header();
        author = header.forgerAddress().address();
        number = Numeric.encodeQuantity(BigInteger.valueOf(blockNumber));
        hash = blockHash;
        parentHash = new Hash(Numeric.prependHexPrefix((String) header.parentId()));
        logsBloom = Numeric.toHexString(header.logsBloom().getBytes());
        transactionsRoot = new Hash(header.sidechainTransactionsMerkleRootHash());
        stateRoot = new Hash(header.stateRoot());
        receiptsRoot = new Hash(header.receiptsRoot());
        miner = header.forgerAddress().address();
        size = Numeric.encodeQuantity(BigInteger.valueOf(header.bytes().length));
        gasLimit = Numeric.encodeQuantity(header.gasLimit());
        gasUsed = Numeric.encodeQuantity(header.gasUsed());
        timestamp = Numeric.encodeQuantity(BigInteger.valueOf(block.timestamp()));
        baseFeePerGas = Numeric.encodeQuantity(header.baseFee());
        transactions = txs;
    }

    public static EthereumBlockView notHydrated(Long blockNumber, Hash blockHash, AccountBlock block) {
        var transactions = AccountBlockUtil.ethereumTransactions(block);
        var txHashes = transactions.stream().map(t -> Numeric.prependHexPrefix(t.id())).collect(Collectors.toList());
        return new EthereumBlockView(blockNumber, blockHash, block, txHashes);
    }

    public static EthereumBlockView hydrated(
        Long blockNumber,
        Hash blockHash,
        AccountBlock block,
        List<EthereumReceipt> receipts
    ) {
        var transactions = AccountBlockUtil.ethereumTransactions(block);
        assert transactions.size() == receipts.size();
        var txViews = IntStream
            .range(0, transactions.size())
            .mapToObj(i -> new EthereumTransactionView(transactions.get(i), receipts.get(i), block.header().baseFee()))
            .collect(Collectors.toList());
        return new EthereumBlockView(blockNumber, blockHash, block, txViews);
    }
}

