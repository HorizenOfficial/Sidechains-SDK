package com.horizen.account.receipt;

import com.horizen.evm.interop.EvmLog;
import com.horizen.evm.utils.Address;
import com.horizen.evm.utils.Hash;
import scorex.core.serialization.ScorexSerializer;
import scorex.util.serialization.Reader;
import scorex.util.serialization.Writer;

import java.util.ArrayList;

public class EthereumLogSerializer<T extends EthereumLog> implements ScorexSerializer<T> {

    private static final EthereumLogSerializer serializer;

    static {
        serializer = new EthereumLogSerializer();
    }

    private EthereumLogSerializer() {
        super();
    }

    public static EthereumLogSerializer getSerializer() {
        return serializer;
    }

    @Override
    public void serialize(EthereumLog log, Writer writer) {
        // consensus data
        writer.putBytes(log.getConsensusLogData().address.toBytes());

        // array of elements of fixed data size (32 bytes)
        int topicsArraySize = log.getConsensusLogData().topics.length;
        writer.putInt(topicsArraySize);
        for (int i = 0; i < topicsArraySize; i++) {
            writer.putBytes(log.getConsensusLogData().topics[i].toBytes());
        }

        byte[] data = log.getConsensusLogData().data;
        writer.putInt(data.length);
        writer.putBytes(data);

        // derived
        // TODO (shall we put these 4? They are the same as belonginh receipt
        writer.putBytes(log.getTransactionHash());
        writer.putInt(log.getTransactionIndex());
        writer.putBytes(log.getBlockHash());
        writer.putInt(log.getBlockNumber());

        writer.putInt(log.getLogIndex());
        writer.putInt(log.getRemoved());
    }

    @Override
    public T parse(Reader reader) {
        // consensus data
        byte[] address = reader.getBytes(20);

        int topicsArraySize = reader.getInt();
        ArrayList<Hash> topics = new ArrayList<>();
        for (int i = 0; i < topicsArraySize; i++) {
            topics.add(Hash.FromBytes(reader.getBytes(Hash.LENGTH)));
        }

        int dataLength = reader.getInt();
        byte[] data = reader.getBytes(dataLength);

        EvmLog consensusDataLog = new EvmLog();
        consensusDataLog.address = Address.FromBytes(address);
        consensusDataLog.topics = topics.toArray(new Hash[0]);
        consensusDataLog.data = data;

        // derived
        // TODO (shall we put these 4? They are the same as belonginh receipt
        byte[] txHash = reader.getBytes(32);
        int txIndex = reader.getInt();
        byte[] blockHash = reader.getBytes(32);
        int blockNumber = reader.getInt();

        int logIndex = reader.getInt();
        int removed = reader.getInt();

        EthereumLog log = new EthereumLog(consensusDataLog);

        log.setTransactionHash(txHash);
        log.setTransactionIndex(txIndex);
        log.setBlockHash(blockHash);
        log.setBlockNumber(blockNumber);
        log.setLogIndex(logIndex);
        log.setRemoved(removed);

        return (T) log;
    }
}
