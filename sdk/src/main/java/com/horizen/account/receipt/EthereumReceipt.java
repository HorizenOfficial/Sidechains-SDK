package com.horizen.account.receipt;

import com.fasterxml.jackson.annotation.*;
import com.google.common.primitives.Bytes;
import com.google.common.primitives.Longs;
import com.horizen.serialization.Views;
import org.web3j.protocol.core.methods.response.Log;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;



@JsonView(Views.Default.class)
public class EthereumReceipt {

    public enum ReceiptStatus { FAILED, SUCCESSFUL}
    public enum ReceiptTxType {LegacyTxType, AccessListTxType, DynamicFeeTxType}

    // consensus data
        /*  From yellow paper
            ----------------------
            the type of the transaction, Rx,
            the status code of the transaction, Rz
            the cumulative gas used in the block containing the transaction receipt as of immediately after the transaction has happened, Ru
            the set of logs created through execution of the transaction, Rl
            and the Bloom filter composed from information in those logs, Rb
         */
    int transactionType;
    int status;
    BigInteger cumulativeGasUsed;
    List<Log> logs;
    byte[] logsBloom;

    // derived types (not part of the patricia trie)
    byte[] transactionHash;
    BigInteger transactionIndex;
    byte[] blockHash;
    BigInteger blockNumber;
    BigInteger gasUsed;
    byte[] contractAddress;

    public EthereumReceipt(
            int transactionType,
            int status,
            BigInteger cumulativeGasUsed,
            List<Log> logs,
            byte[] logsBloom
    ) {
        this.transactionType = transactionType;
        this.status = status;
        this.cumulativeGasUsed = cumulativeGasUsed;
        this.logs = logs;
        this.logsBloom = Arrays.copyOf(logsBloom, logsBloom.length);


        this.transactionHash = null;
        this.transactionIndex = BigInteger.valueOf(-1);
        this.blockHash = null;
        this.blockNumber = BigInteger.valueOf(-1);;
        this.gasUsed = BigInteger.valueOf(-1);;
        this.contractAddress = null;
    }

    public int getTransactionType() { return transactionType; }
    public void setTransactionType(int transactionType) {
        this.transactionType = transactionType;
    }

    public byte[] getTransactionHash() {
        return this.transactionHash;
    }
    public void setTransactionHash(byte[] transactionHash) {
        this.transactionHash = Arrays.copyOf(transactionHash, transactionHash.length);
    }

    public BigInteger getTransactionIndex() { return this.transactionIndex; }
    public void setTransactionIndex(BigInteger transactionIndex) {
        this.transactionIndex = transactionIndex;
    }

    public byte[] getBlockHash() {
        return this.blockHash;
    }
    public void setBlockHash(byte[] blockHash) {
        this.blockHash = Arrays.copyOf(blockHash, blockHash.length);
    }

    public BigInteger getBlockNumber() { return this.blockNumber;}
    public void setBlockNumber(BigInteger blockNumber) {
        this.blockNumber = blockNumber;
    }

    public BigInteger getCumulativeGasUsed() { return this.cumulativeGasUsed; }
    public void setCumulativeGasUsed(BigInteger cumulativeGasUsed) {
        this.cumulativeGasUsed = cumulativeGasUsed;
    }

    public BigInteger getGasUsed() {
        return this.gasUsed;
    }
    public void setGasUsed(BigInteger gasUsed) {
        this.gasUsed = gasUsed;
    }

    public byte[] getContractAddress() {
        return this.contractAddress;
    }
    public void setContractAddress(byte[] contractAddress) {
        this.contractAddress = Arrays.copyOf(contractAddress, contractAddress.length);
    }

    public int getStatus() {
        return this.status;
    }
    public void setStatus(int status) {
        this.status = status;
    }

    public boolean isStatusOK() {
        return status == ReceiptStatus.SUCCESSFUL.ordinal();
    }

    public ReceiptTxType getTxType() {
        switch(transactionType) {
            case 0:   return ReceiptTxType.LegacyTxType;
            case 1:   return ReceiptTxType.AccessListTxType;
            case 2:   return ReceiptTxType.DynamicFeeTxType;
        }
        return null;
    }

    public List<Log> getLogs() {
        return this.logs;
    }
    public void setLogs(List<Log> logs) {
        this.logs = logs;
    }

    public byte[] getLogsBloom() {
        return this.logsBloom;
    }
    public void setLogsBloom(byte[] logsBloom) {
        this.logsBloom = Arrays.copyOf(logsBloom, logsBloom.length);
    }

    public byte[] RLP_encode() {
        // TODO do the real encoding
        // encode only consensus data
        // Note: depends on transactionType
        // https://github.com/ethereum/go-ethereum/blob/master/core/types/receipt.go
        // https://github.com/ethereumj/ethereumj/blob/master/ethereumj-core/src/main/java/org/ethereum/util/RLP.java
        return Bytes.concat(
        BigInteger.valueOf(transactionType).toByteArray(),
                BigInteger.valueOf(status).toByteArray(),
                cumulativeGasUsed.toByteArray());
    }
}
