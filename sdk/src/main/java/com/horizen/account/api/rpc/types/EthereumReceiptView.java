package com.horizen.account.api.rpc.types;

import com.horizen.account.receipt.EthereumReceipt;
import com.horizen.account.transaction.EthereumTransaction;
import io.horizen.evm.Address;
import io.horizen.evm.Hash;
import scala.collection.JavaConverters;

import java.math.BigInteger;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class EthereumReceiptView {
    // block data
    public final Hash blockHash;
    public final BigInteger blockNumber;
    public final BigInteger transactionIndex;

    // transaction data
    public final Hash transactionHash;
    public final BigInteger type;
    public final Address from;
    public final Address to;
    public final BigInteger effectiveGasPrice;

    // receipt data
    public final Address contractAddress;
    public final List<EthereumLogView> logs;
    public final byte[] logsBloom;
    public final BigInteger gasUsed;
    public final BigInteger cumulativeGasUsed;
    public final BigInteger status;

    public EthereumReceiptView(EthereumReceipt receipt, EthereumTransaction tx, BigInteger baseFee, int firstLogIndex) {
        blockHash = new Hash(receipt.blockHash());
        blockNumber = BigInteger.valueOf(receipt.blockNumber());
        transactionIndex = BigInteger.valueOf(receipt.transactionIndex());
        transactionHash = new Hash(receipt.transactionHash());
        type = BigInteger.valueOf(tx.version());
        from = tx.getFromAddress();
        to = tx.getToAddress();
        effectiveGasPrice = tx.getEffectiveGasPrice(baseFee);
        contractAddress = receipt.contractAddress().getOrElse(() -> null);
        var consensusLogs = JavaConverters.seqAsJavaList(receipt.consensusDataReceipt().logs());
        logs = IntStream
            .range(0, consensusLogs.size())
            .mapToObj(i -> new EthereumLogView(receipt, consensusLogs.get(i), firstLogIndex + i))
            .collect(Collectors.toList());
        logsBloom = receipt.consensusDataReceipt().logsBloom().getBytes();
        gasUsed = receipt.gasUsed();
        cumulativeGasUsed = receipt.consensusDataReceipt().cumulativeGasUsed();
        status = BigInteger.valueOf(receipt.consensusDataReceipt().status());
    }
}
