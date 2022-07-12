package com.horizen.account.receipt;

import com.horizen.utils.ListSerializer;
import scorex.core.serialization.ScorexSerializer;
import scorex.util.serialization.Reader;
import scorex.util.serialization.Writer;

import java.math.BigInteger;
import java.util.List;

public class EthereumReceiptJavaSerializer implements ScorexSerializer<EthereumReceiptJava> {

    private static final EthereumReceiptJavaSerializer serializer;

    static {
        serializer = new EthereumReceiptJavaSerializer();
    }
    static ListSerializer<EthereumLogJava> logsSerializer =
            new ListSerializer<>(EthereumLogJavaSerializer.getSerializer());

    private EthereumReceiptJavaSerializer() {
        super();
    }

    public static EthereumReceiptJavaSerializer getSerializer() {
        return serializer;
    }

    @Override
    public void serialize(EthereumReceiptJava receipt, Writer writer) {
        // consensus data
        writer.putInt(receipt.getTransactionType());

        writer.putInt(receipt.getStatus());

        byte[] cumGasUsedBytes = receipt.getCumulativeGasUsed().toByteArray();
        writer.putInt(cumGasUsedBytes.length);
        writer.putBytes(cumGasUsedBytes);

        ListSerializer<EthereumLogJava> logsSerializer =
                new ListSerializer<>(EthereumLogJavaSerializer.getSerializer());
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
    public EthereumReceiptJava parse(Reader reader) {

        int transactionType = reader.getInt();
        int status = reader.getInt();

        int cumGasUsedLength = reader.getInt();
        BigInteger cumGasUsed = new BigInteger(reader.getBytes(cumGasUsedLength));

        // TODO logs

        List<EthereumLogJava> logs = logsSerializer.parse(reader);

        int bloomsLength = reader.getInt();
        byte[] blooms = reader.getBytes(bloomsLength);

        EthereumReceiptJava receipt = new EthereumReceiptJava(transactionType, status, cumGasUsed, logs, blooms);

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
