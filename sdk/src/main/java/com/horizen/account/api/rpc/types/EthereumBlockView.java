package com.horizen.account.api.rpc.types;

import com.horizen.account.block.AccountBlock;
import com.horizen.account.receipt.EthereumReceipt;
import com.horizen.account.utils.AccountBlockUtil;
import com.horizen.evm.utils.Address;
import com.horizen.evm.utils.Hash;
import org.web3j.utils.Numeric;

import java.math.BigInteger;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class EthereumBlockView {
    public final BigInteger number;
    public final Hash hash;
    public final Hash parentHash;
    public final byte[] logsBloom;
    public final Hash transactionsRoot;
    public final Hash stateRoot;
    public final Hash receiptsRoot;
    public final Address miner;
    public final BigInteger size;
    public final BigInteger gasLimit;
    public final BigInteger gasUsed;
    public final BigInteger timestamp;
    public final List<?> transactions;
    public final BigInteger baseFeePerGas;
    // mixHash is set to a VRF output to support the PREVRANDAO EVM-opcode, just like Ethereum does since The Merge
    public final Hash mixHash;

    // we do not have uncles
    public final Hash[] uncles = new Hash[0];
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

    private EthereumBlockView(Long blockNumber, Hash blockHash, AccountBlock block, List<?> txs) {
        var header = block.header();
        number = BigInteger.valueOf(blockNumber);
        hash = blockHash;
        parentHash = new Hash(Numeric.prependHexPrefix((String) header.parentId()));
        logsBloom = header.logsBloom().getBytes();
        transactionsRoot = new Hash(header.sidechainTransactionsMerkleRootHash());
        stateRoot = new Hash(header.stateRoot());
        receiptsRoot = new Hash(header.receiptsRoot());
        miner = header.forgerAddress().address();
        size = BigInteger.valueOf(header.bytes().length);
        gasLimit = header.gasLimit();
        gasUsed = header.gasUsed();
        timestamp = BigInteger.valueOf(block.timestamp());
        baseFeePerGas = header.baseFee();
        transactions = txs;
        mixHash = new Hash(header.vrfOutput().bytes());
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

