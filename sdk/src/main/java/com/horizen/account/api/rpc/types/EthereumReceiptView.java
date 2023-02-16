package com.horizen.account.api.rpc.types;

import com.fasterxml.jackson.annotation.JsonView;
import com.horizen.account.receipt.EthereumReceipt;
import com.horizen.account.transaction.EthereumTransaction;
import com.horizen.evm.utils.Address;
import com.horizen.evm.utils.Hash;
import com.horizen.serialization.Views;
import scala.collection.JavaConverters;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

@JsonView(Views.Default.class)
public class EthereumReceiptView {
    public final BigInteger type;
    public final Hash transactionHash;
    public final BigInteger transactionIndex;
    public final Hash blockHash;
    public final BigInteger blockNumber;
    public final Address from;
    public final Address to;
    public final BigInteger cumulativeGasUsed;
    public final BigInteger gasUsed;
    public final Address contractAddress;
    public final List<EthereumLogView> logs;
    public final byte[] logsBloom;
    public final BigInteger status;
    public final BigInteger effectiveGasPrice;

    public EthereumReceiptView(EthereumReceipt receipt, EthereumTransaction tx, BigInteger baseFee, int firstLogIndex) {
        type = BigInteger.valueOf(tx.version());
        transactionHash = new Hash(receipt.transactionHash());
        transactionIndex = BigInteger.valueOf(receipt.transactionIndex());
        blockHash = new Hash(receipt.blockHash());
        blockNumber = BigInteger.valueOf(receipt.blockNumber());
        from = tx.getFromAddress();
        to = tx.getToAddress();
        cumulativeGasUsed = receipt.consensusDataReceipt().cumulativeGasUsed();
        gasUsed = receipt.gasUsed();
        contractAddress = receipt.contractAddress().getOrElse(() -> null);
        var consensusLogs = JavaConverters.seqAsJavaList(receipt.consensusDataReceipt().logs());
        logs = new ArrayList<>(consensusLogs.size());
        for (var i = 0; i < consensusLogs.size(); i++) {
            logs.add(new EthereumLogView(receipt, consensusLogs.get(i), firstLogIndex + i));
        }
        logsBloom = receipt.consensusDataReceipt().logsBloom().getBytes();
        status = BigInteger.valueOf(receipt.consensusDataReceipt().status());
        // calculate effective gas price, this will work for both legacy and EIP1559 TXs
        effectiveGasPrice = tx.getEffectiveGasPrice(baseFee);
    }
}
