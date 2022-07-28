package com.horizen.account.api.rpc.types;

import com.fasterxml.jackson.annotation.JsonView;
import com.horizen.account.receipt.EthereumLog;
import com.horizen.account.receipt.EthereumReceipt;
import com.horizen.account.transaction.EthereumTransaction;
import com.horizen.serialization.Views;
import com.horizen.utils.BytesUtils;
import org.web3j.utils.Numeric;
import scala.collection.immutable.List;

@JsonView(Views.Default.class)
public class EthereumReceiptView {
    private String transactionHash;
    private String transactionIndex;
    private String blockHash;
    private String blockNumber;
    private String from;
    private String to;
    private String cumulativeGasUsed;
    private String gasUsed;
    private String contractAddress;
    private List<EthereumLog> logs;
    private String logsBloom;
    private String root;
    private String status;
    private String effectiveGasPrice;

    public EthereumReceiptView() {
    }

    public EthereumReceiptView(EthereumReceipt receipt, EthereumTransaction tx) {
        transactionHash = Numeric.toHexString(receipt.transactionHash());
        transactionIndex = String.valueOf(receipt.transactionIndex());
        blockHash = Numeric.toHexString(receipt.blockHash());
        blockNumber = String.valueOf(receipt.blockNumber());
        from = (tx.getFrom() != null) ? Numeric.toHexString(tx.getFrom().address()) : "";
        to = (tx.getTo() != null) ? Numeric.toHexString(tx.getTo().address()) : "";
        cumulativeGasUsed = Numeric.toHexStringWithPrefix(receipt.consensusDataReceipt().cumulativeGasUsed());
        gasUsed = Numeric.toHexStringWithPrefix(receipt.gasUsed());
        contractAddress = Numeric.toHexString(receipt.contractAddress());
        logs = receipt.deriveFullLogs().seq().toList();
        logsBloom = Numeric.toHexString(receipt.consensusDataReceipt().logsBloom());
        status = String.valueOf(receipt.consensusDataReceipt().status());
        effectiveGasPrice = Numeric.toHexStringWithPrefix(tx.getGasPrice());
    }

    public String getTransactionHash() { return this.transactionHash; }
    public String getTransactionIndex() { return this.transactionIndex; }
    public String getBlockHash() { return this.blockHash; }
    public String getBlockNumber() { return this.blockNumber; }
    public String getFrom() { return this.from; }
    public String getTo() { return this.to; }
    public String getCumulativeGasUsed() { return this.cumulativeGasUsed; }
    public String getGasUsed() { return this.gasUsed; }
    public String getContractAddress() { return this.contractAddress; }
    public List<EthereumLog> getLogs() { return this.logs; }
    public String getLogsBloom() { return this.logsBloom; }
    public String getRoot() { return this.root; }
    public String getStatus() { return this.status; }
    public String getEffectiveGasPrice() { return this.effectiveGasPrice; }
}
