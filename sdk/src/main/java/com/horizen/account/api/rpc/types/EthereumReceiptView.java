package com.horizen.account.api.rpc.types;

import com.fasterxml.jackson.annotation.JsonView;
import com.horizen.account.receipt.EthereumReceipt;
import com.horizen.account.transaction.EthereumTransaction;
import com.horizen.evm.utils.Address;
import com.horizen.evm.utils.Hash;
import com.horizen.serialization.Views;
import org.web3j.utils.Numeric;
import scala.collection.JavaConverters;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

@JsonView(Views.Default.class)
public class EthereumReceiptView {
    public final String type;
    public final Hash transactionHash;
    public final String transactionIndex;
    public final Hash blockHash;
    public final String blockNumber;
    public final Address from;
    public final Address to;
    public final String cumulativeGasUsed;
    public final String gasUsed;
    public final Address contractAddress;
    public final List<EthereumLogView> logs;
    public final String logsBloom;
    public final String status;
    public final String effectiveGasPrice;

    public EthereumReceiptView(EthereumReceipt receipt, EthereumTransaction tx, BigInteger baseFee, int firstLogIndex) {
        type = Numeric.toHexString(new byte[] { tx.version() });
        transactionHash = new Hash(receipt.transactionHash());
        transactionIndex = Numeric.encodeQuantity(BigInteger.valueOf(receipt.transactionIndex()));
        blockHash = new Hash(receipt.blockHash());
        blockNumber = Numeric.encodeQuantity(BigInteger.valueOf(receipt.blockNumber()));
        from = tx.getFromAddress();
        to = tx.getToAddress();
        cumulativeGasUsed = Numeric.encodeQuantity(receipt.consensusDataReceipt().cumulativeGasUsed());
        gasUsed = Numeric.encodeQuantity(receipt.gasUsed());
        contractAddress = receipt.contractAddress().getOrElse(() -> null);
        var consensusLogs = JavaConverters.seqAsJavaList(receipt.consensusDataReceipt().logs());
        logs = new ArrayList<>(consensusLogs.size());
        for (var i = 0; i < consensusLogs.size(); i++) {
            logs.add(new EthereumLogView(receipt, consensusLogs.get(i), firstLogIndex + i));
        }
        logsBloom = Numeric.toHexString(receipt.consensusDataReceipt().logsBloom().getBytes());
        status = Numeric.prependHexPrefix(Integer.toHexString(receipt.consensusDataReceipt().status()));
        // calculate effective gas price, this will work for both legacy and EIP1559 TXs
        effectiveGasPrice = Numeric.encodeQuantity(tx.getEffectiveGasPrice(baseFee));

    }
}
