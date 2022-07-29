package com.horizen.account.api.rpc.types;

import com.fasterxml.jackson.annotation.JsonView;
import com.horizen.account.receipt.EthereumLog;
import com.horizen.account.receipt.EthereumReceipt;
import com.horizen.account.transaction.EthereumTransaction;
import com.horizen.account.utils.Account;
import com.horizen.serialization.Views;
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
        transactionIndex = Numeric.prependHexPrefix(Integer.toHexString(receipt.transactionIndex()));
        blockHash = Numeric.toHexString(receipt.blockHash());
        blockNumber = Numeric.prependHexPrefix(Integer.toHexString(receipt.blockNumber()));
        from = (tx.getFrom() != null) ? Numeric.toHexString(tx.getFrom().address()) : null;
        to = (tx.getTo() != null) ? Numeric.toHexString(tx.getTo().address()) : null;
        cumulativeGasUsed = Numeric.toHexStringWithPrefix(receipt.consensusDataReceipt().cumulativeGasUsed());
        gasUsed = Numeric.toHexStringWithPrefix(receipt.gasUsed());
        contractAddress = receipt.contractAddress().length != Account.ADDRESS_SIZE ? null : Numeric.toHexString(receipt.contractAddress());
        logs = receipt.deriveFullLogs().seq().toList();
        logsBloom = Numeric.toHexString(receipt.consensusDataReceipt().logsBloom());
        status = Numeric.prependHexPrefix(Integer.toHexString(receipt.consensusDataReceipt().status()));
        effectiveGasPrice = (tx.getGasPrice() != null) ? Numeric.toHexStringWithPrefix(tx.getGasPrice()) : null;
    }

    public String getTransactionHash() {
        return this.transactionHash;
    }

    public String getTransactionIndex() {
        return this.transactionIndex;
    }

    public String getBlockHash() {
        return this.blockHash;
    }

    public String getBlockNumber() {
        return this.blockNumber;
    }

    public String getFrom() {
        return this.from;
    }

    public String getTo() {
        return this.to;
    }

    public String getCumulativeGasUsed() {
        return this.cumulativeGasUsed;
    }

    public String getGasUsed() {
        return this.gasUsed;
    }

    public String getContractAddress() {
        return this.contractAddress;
    }

    public List<EthereumLog> getLogs() {
        return this.logs;
    }

    public String getLogsBloom() {
        return this.logsBloom;
    }

    public String getRoot() {
        return this.root;
    }

    public String getStatus() {
        return this.status;
    }

    public String getEffectiveGasPrice() {
        return this.effectiveGasPrice;
    }
}
