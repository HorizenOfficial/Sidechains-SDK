package com.horizen.account.receipt;

import com.fasterxml.jackson.annotation.JsonView;
import com.horizen.evm.interop.EvmLog;
import com.horizen.evm.utils.Address;
import com.horizen.evm.utils.Hash;
import com.horizen.serialization.Views;
import com.horizen.utils.BytesUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.web3j.rlp.*;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


@JsonView(Views.Default.class)
public class EthereumLog {

    private static final Logger log = LogManager.getLogger(EthereumLog.class);

    // consensus data
    EvmLog consensusLogData;

    // Derived fields. These fields are filled in by the node
    // but not secured by consensus.
    // block in which the transaction was included
    BigInteger blockNumber;
    // hash of the transaction
    byte[] transactionHash;
    // index of the transaction in the block
    BigInteger transactionIndex;
    // hash of the block in which the transaction was included
    byte[] blockHash;
    // index of the log in the block logs
    int logIndex;

    // The Removed field is true if this log was reverted due to a chain reorganisation.
    // You must pay attention to this field if you receive logs through a filter query.
    Boolean removed;


    public EthereumLog(EvmLog log) {
        this.consensusLogData = log;

        this.blockNumber = BigInteger.valueOf(-1);
        this.transactionHash = null;
        this.transactionIndex = BigInteger.valueOf(-1);
        this.blockHash = null;
        this.logIndex = -1;
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

    public EvmLog getConsensusLogData() {
        return this.consensusLogData;
    }
    public void setConsensusLogData(EvmLog log) { this.consensusLogData = log; }

    public static byte[] rlpEncode(EthereumLog r) {
        List<RlpType> values = asRlpValues(r);
        RlpList rlpList = new RlpList(values);
        byte[] encoded = RlpEncoder.encode(rlpList);
        return encoded;
    }

    public static EthereumLog rlpDecode(byte[] rlpData) {

        RlpList rlpList = RlpDecoder.decode(rlpData);
        RlpList values = (RlpList) rlpList.getValues().get(0);

        EvmLog decodedLog = new EvmLog();

        byte[] addressBytes = ((RlpString) values.getValues().get(0)).getBytes();
        decodedLog.address = Address.FromBytes(addressBytes);

        RlpList topicsRlp = ((RlpList) values.getValues().get(1));
        List<Hash> hashList = new ArrayList<>();
        int topicsListSize = topicsRlp.getValues().size();
        if (topicsListSize > 0) {
            // loop on list and decode all topics
            for (int i = 0; i < topicsListSize; ++i) {
                byte[] topicBytes = ((RlpString) topicsRlp.getValues().get(i)).getBytes();
                hashList.add(Hash.FromBytes(topicBytes));
            }
        }
        decodedLog.topics = hashList.toArray(new Hash[0]);

        decodedLog.data = ((RlpString) values.getValues().get(2)).getBytes();

        return new EthereumLog(decodedLog);
    }

    public static List<RlpType> asRlpValues(EthereumLog log) {
        List<RlpType> result = new ArrayList<>();
        List<RlpType> rlpTopics = new ArrayList<>();

        result.add(RlpString.create(log.getConsensusLogData().address.toBytes()));

        Hash[] topics = log.getConsensusLogData().topics;
        for (int i = 0; i < topics.length; i++) {
            rlpTopics.add(RlpString.create(topics[i].toBytes()));
        }
        result.add(new RlpList(rlpTopics));

        result.add(RlpString.create(log.getConsensusLogData().data));

        return result;
    }

    public byte[] createBloom() {
        // we can create bloom filter out of a log

        /* see: https://github.com/ethereum/go-ethereum/blob/55f914a1d764dac4bd37a48173092b1f5c3b186d/core/types/bloom9.go

            // CreateBloom creates a bloom filter out of the give Receipts (+Logs)
            func CreateBloom(receipts Receipts) Bloom {
                buf := make([]byte, 6)
                var bin Bloom
                for _, receipt := range receipts {
                    for _, log := range receipt.Logs {
                        bin.add(log.Address.Bytes(), buf)
                        for _, b := range log.Topics {
                            bin.add(b[:], buf)
                        }
                    }
                }
                return bin
            }

            // add is internal version of Add, which takes a scratch buffer for reuse (needs to be at least 6 bytes)
            func (b *Bloom) add(d []byte, buf []byte) {
                i1, v1, i2, v2, i3, v3 := bloomValues(d, buf)
                b[i1] |= v1
                b[i2] |= v2
                b[i3] |= v3
            }

            // bloomValues returns the bytes (index-value pairs) to set for the given data
            func bloomValues(data []byte, hashbuf []byte) (uint, byte, uint, byte, uint, byte) {
                sha := hasherPool.Get().(crypto.KeccakState)
                sha.Reset()
                sha.Write(data)
                sha.Read(hashbuf)
                hasherPool.Put(sha)
                // The actual bits to flip
                v1 := byte(1 << (hashbuf[1] & 0x7))
                v2 := byte(1 << (hashbuf[3] & 0x7))
                v3 := byte(1 << (hashbuf[5] & 0x7))
                // The indices for the bytes to OR in
                i1 := BloomByteLength - uint((binary.BigEndian.Uint16(hashbuf)&0x7ff)>>3) - 1
                i2 := BloomByteLength - uint((binary.BigEndian.Uint16(hashbuf[2:])&0x7ff)>>3) - 1
                i3 := BloomByteLength - uint((binary.BigEndian.Uint16(hashbuf[4:])&0x7ff)>>3) - 1

                return i1, v1, i2, v2, i3, v3
            }

         */
        // TODO shall we use libevm implementation?
        return new byte[0];
    }

    @Override
    public String toString() {
        String topicsStr = "topics{";
        Hash[] topics = getConsensusLogData().topics;
        for (int i = 0; i < topics.length; i++) {
            topicsStr = topicsStr.concat(" " + BytesUtils.toHexString(topics[i].toBytes()));
        }
        topicsStr = topicsStr.concat("}");

        return String.format(
                "EthereumLog{address=%s, topics=%s, data=%s}",
                BytesUtils.toHexString(consensusLogData.address.toBytes()),
                topicsStr,
                BytesUtils.toHexString(consensusLogData.data));
    }
}
