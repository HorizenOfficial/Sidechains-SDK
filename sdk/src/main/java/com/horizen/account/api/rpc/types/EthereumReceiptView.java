package com.horizen.account.api.rpc.types;

import com.fasterxml.jackson.annotation.JsonView;
import com.horizen.account.receipt.EthereumReceipt;
import com.horizen.account.transaction.EthereumTransaction;
import com.horizen.account.utils.Account;
import com.horizen.serialization.Views;
import org.web3j.utils.Numeric;
import scala.collection.JavaConverters;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

@JsonView(Views.Default.class)
public class EthereumReceiptView {
    public final String type;
    public final String transactionHash;
    public final String transactionIndex;
    public final String blockHash;
    public final String blockNumber;
    public final String from;
    public final String to;
    public final String cumulativeGasUsed;
    public final String gasUsed;
    public final String contractAddress;
    public final List<EthereumLogView> logs;
    public final String logsBloom;
    public final String status;
    public final String effectiveGasPrice;

    public EthereumReceiptView(EthereumReceipt receipt, EthereumTransaction tx, BigInteger baseFee) {
        type = Numeric.toHexString(new byte[]{tx.version()});
        transactionHash = Numeric.toHexString(receipt.transactionHash());
        transactionIndex = Numeric.encodeQuantity(BigInteger.valueOf(receipt.transactionIndex()));
        blockHash = Numeric.toHexString(receipt.blockHash());
        blockNumber = Numeric.encodeQuantity(BigInteger.valueOf(receipt.blockNumber()));
        from = (tx.getFrom() != null) ? Numeric.toHexString(tx.getFrom().address()) : null;
        to = (tx.getTo().isPresent()) ? Numeric.toHexString(tx.getTo().get().address()) : null;
        cumulativeGasUsed = Numeric.encodeQuantity(receipt.consensusDataReceipt().cumulativeGasUsed());
        gasUsed = Numeric.encodeQuantity(receipt.gasUsed());
        contractAddress = receipt.contractAddress().length != Account.ADDRESS_SIZE ? null : Numeric.toHexString(receipt.contractAddress());
        var consensusLogs = JavaConverters.seqAsJavaList(receipt.consensusDataReceipt().logs());
        logs = new ArrayList<>(consensusLogs.size());
        for (var i = 0; i < consensusLogs.size(); i++) {
            // TODO: the logIndex should refer to the index within the block, but this assigns it based on the index within the receipt
            logs.add(new EthereumLogView(receipt, consensusLogs.get(i), i));
        }
        logsBloom = Numeric.toHexString(receipt.consensusDataReceipt().logsBloom().getBytes());
        status = Numeric.prependHexPrefix(Integer.toHexString(receipt.consensusDataReceipt().status()));
        // calculate effective gas price, this will work for both legacy and EIP1559 TXs
        effectiveGasPrice = Numeric.encodeQuantity(tx.getEffectiveGasPrice(baseFee));

    }
}
