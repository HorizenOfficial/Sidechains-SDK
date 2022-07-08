package com.horizen.account.receipt;

import com.horizen.utils.ListSerializer;
import scorex.core.serialization.ScorexSerializer;
import scorex.util.serialization.Reader;
import scorex.util.serialization.Writer;

import java.math.BigInteger;
import java.util.List;

public class EthereumReceiptSerializer implements ScorexSerializer<EthereumReceipt> {

    private static final EthereumReceiptSerializer serializer;

    static {
        serializer = new EthereumReceiptSerializer();
    }
    static ListSerializer<EthereumLog> logsSerializer =
            new ListSerializer<>(EthereumLogSerializer.getSerializer());

    private EthereumReceiptSerializer() {
        super();
    }

    public static EthereumReceiptSerializer getSerializer() {
        return serializer;
    }

    @Override
    public void serialize(EthereumReceipt receipt, Writer writer) {
        // consensus data
        writer.putInt(receipt.getTransactionType());

        writer.putInt(receipt.getStatus());

        byte[] cumGasUsedBytes = receipt.getCumulativeGasUsed().toByteArray();
        writer.putInt(cumGasUsedBytes.length);
        writer.putBytes(cumGasUsedBytes);

        ListSerializer<EthereumLog> logsSerializer =
                new ListSerializer<>(EthereumLogSerializer.getSerializer());
        logsSerializer.serialize(receipt.getLogs(), writer);

        byte[] bloomBytes = receipt.getLogsBloom();
        writer.putInt(bloomBytes.length);
        writer.putBytes(bloomBytes);

        // derived
        writer.putBytes(receipt.getTransactionHash());
        writer.putInt(receipt.getTransactionIndex());
        writer.putBytes(receipt.getBlockHash());
        writer.putInt(receipt.getBlockNumber());

        byte[] gasUsedBytes = receipt.getGasUsed().toByteArray();
        writer.putInt(gasUsedBytes.length);
        writer.putBytes(gasUsedBytes);

        writer.putBytes(receipt.getContractAddress());
    }

    @Override
    public EthereumReceipt parse(Reader reader) {

        int transactionType = reader.getInt();
        int status = reader.getInt();

        int cumGasUsedLength = reader.getInt();
        BigInteger cumGasUsed = new BigInteger(reader.getBytes(cumGasUsedLength));

        // TODO logs

        List<EthereumLog> logs = logsSerializer.parse(reader);

        int bloomsLength = reader.getInt();
        byte[] blooms = reader.getBytes(bloomsLength);

        EthereumReceipt receipt = new EthereumReceipt(transactionType, status, cumGasUsed, logs, blooms);

        byte[] txHash = reader.getBytes(32);
        int txIndex = reader.getInt();
        byte[] blockHash = reader.getBytes(32);
        int blockNumber = reader.getInt();

        int gasUsedLength = reader.getInt();
        BigInteger gasUsed = new BigInteger(reader.getBytes(gasUsedLength));

        byte[] contractAddress = reader.getBytes(20);

        receipt.setGasUsed(gasUsed);
        receipt.setTransactionHash(txHash);
        receipt.setTransactionIndex(txIndex);
        receipt.setBlockHash(blockHash);
        receipt.setBlockNumber(blockNumber);
        receipt.setContractAddress(contractAddress);

        return receipt;
    }
}
