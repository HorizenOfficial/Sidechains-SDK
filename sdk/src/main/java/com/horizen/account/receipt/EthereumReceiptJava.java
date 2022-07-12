package com.horizen.account.receipt;

import com.fasterxml.jackson.annotation.*;
import com.horizen.serialization.Views;
import com.horizen.utils.BytesUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.web3j.rlp.*;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;



@JsonView(Views.Default.class)
public class EthereumReceiptJava {

    // TODO wrap consensus data into a separate class and move these impl to scala

    private static final Logger log = LogManager.getLogger(EthereumReceiptJava.class);

    public enum ReceiptStatus {FAILED, SUCCESSFUL}

    // all these versions are supported by go eth; but in w3j apparently only 0 and 2:
    //   org/web3j/crypto/transaction/type/TransactionType.java
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
    List<EthereumLogJava> logs;
    byte[] logsBloom;


    // derived types (not part of the rlp encoding for receipt hash root)
    byte[] transactionHash;
    Integer transactionIndex;
    byte[] blockHash;
    Integer blockNumber;
    BigInteger gasUsed;
    byte[] contractAddress;

    public EthereumReceiptJava() {
        this(0, 0, BigInteger.valueOf(-1), new ArrayList<>(), new byte[256]);
    }

    public EthereumReceiptJava(
            int transactionType,
            int status,
            BigInteger cumulativeGasUsed,
            List<EthereumLogJava> logs,
            byte[] logsBloom
    ) {
        this.transactionType = transactionType;
        this.status = status;
        this.cumulativeGasUsed = cumulativeGasUsed;
        this.logs = logs;

        // we should derive blooms from logs via createLogsBloom()
        if (logsBloom != null) {
            this.logsBloom = Arrays.copyOf(logsBloom, logsBloom.length);
        }

        this.transactionHash = new byte[32];
        this.transactionIndex = -1;
        this.blockHash = new byte[32];
        this.blockNumber = -1;
        this.gasUsed = BigInteger.valueOf(-1);
        this.contractAddress = new byte[20];
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

    public Integer getTransactionIndex() { return this.transactionIndex; }
    public void setTransactionIndex(Integer transactionIndex) {
        this.transactionIndex = transactionIndex;
    }

    public byte[] getBlockHash() {
        return this.blockHash;
    }
    public void setBlockHash(byte[] blockHash) {
        this.blockHash = Arrays.copyOf(blockHash, blockHash.length);
    }

    public Integer getBlockNumber() { return this.blockNumber;}
    public void setBlockNumber(Integer blockNumber) {
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
        // TODO assert transaction type is in valid range
        throw new IllegalArgumentException("invalid transaction type:" + transactionType);
    }

    public List<EthereumLogJava> getLogs() {
        return this.logs;
    }
    public void setLogs(List<EthereumLogJava> logs) {
        this.logs = logs;
    }

    public byte[] getLogsBloom() {
        return this.logsBloom;
    }
    public void setLogsBloom(byte[] logsBloom) {
        this.logsBloom = Arrays.copyOf(logsBloom, logsBloom.length);
    }
    public void setLogsBloom() {
        // creates blooms out of logs content
        this.logsBloom = createLogsBloom();
    }

    public static byte[] rlpEncode(EthereumReceiptJava r) {
        List<RlpType> values = asRlpValues(r);
        RlpList rlpList = new RlpList(values);
        byte[] encoded = RlpEncoder.encode(rlpList);

        if (!r.getTxType().equals(ReceiptTxType.LegacyTxType) ) {
            // add byte for versioned type support
            return ByteBuffer.allocate(encoded.length + 1)
                    .put((byte)r.getTxType().ordinal())
                    .put(encoded)
                    .array();
        }
        return encoded;
    }

    public static EthereumReceiptJava rlpDecode(byte[] rlpData) {
        if (rlpData == null || rlpData.length == 0) {
            log.error("Invalid rlp data");
            return null;
        }

        // handle tx type
        int b0 = rlpData[0];

        if (b0 == 1 || b0 == 2) {
            ReceiptTxType rt = ReceiptTxType.values()[b0];
            return decodeTyped(rt, Arrays.copyOfRange(rlpData, 1, rlpData.length));
        } else {
            return decodeLegacy(rlpData);
        }
    }

    public static EthereumReceiptJava decodeTyped(ReceiptTxType rt, byte[] rlpData) {
        EthereumReceiptJava receipt = decodeLegacy(rlpData);
        if (receipt != null)
            receipt.setTransactionType(rt.ordinal());
        return receipt;
    }

    public static EthereumReceiptJava decodeLegacy(byte[] rlpData) {

        RlpList rlpList = RlpDecoder.decode(rlpData);
        RlpList values = (RlpList) rlpList.getValues().get(0);

        byte[] postTxState = ((RlpString) values.getValues().get(0)).getBytes();
        int status;
        if (postTxState.length == 0) {
            status = 0;
        } else {
            if (postTxState.length != 1 || postTxState[0] != 1) {
                log.error("Invalid rlp postTxState data");
                return null;
            }
            status = 1;
        }

        BigInteger cumulativeGasUsed = ((RlpString) values.getValues().get(1)).asPositiveBigInteger();

        byte[] logsBloom = ((RlpString) values.getValues().get(2)).getBytes();

        RlpList logList = ((RlpList) values.getValues().get(3));
        List<EthereumLogJava> logs = new ArrayList<>(0);
        int logsListSize = logList.getValues().size();
        if (logsListSize > 0) {
            // loop on list and decode all logs
            for (int i = 0; i < logsListSize; ++i) {
                var log = EthereumLogJava.rlpDecode((RlpList) logList.getValues().get(i));
                logs.add(new EthereumLogJava(log));
            }
        }

        return new EthereumReceiptJava(ReceiptTxType.LegacyTxType.ordinal(), status, cumulativeGasUsed,
                logs, logsBloom);
    }

    public static List<RlpType> asRlpValues(EthereumReceiptJava r) {
        List<RlpType> result = new ArrayList<>();

        byte[] postTxState = r.status == 1 ? new byte[]{1} : new byte[0];

        result.add(RlpString.create(postTxState));
        result.add(RlpString.create(r.getCumulativeGasUsed()));

        //bloom filters
        result.add(RlpString.create(r.getLogsBloom()));

        // logs
        List<RlpType> rlpLogs = new ArrayList<>();
        for (int i = 0; i < r.getLogs().size(); i++)
        {
            var log = r.getLogs().get(i);
            rlpLogs.add(new RlpList(EthereumLogJava.asRlpValues(log)));
        }

        result.add(new RlpList(rlpLogs));
        return result;
    }

    public byte[] createLogsBloom() {
        // we can create bloom filter out of a log or out of a receipt (taking its log set)

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
        return new byte[256];
    }

    @Override
    public String toString() {

        String logsString = "logs{";
        List<EthereumLogJava> logList = getLogs();
        for (EthereumLogJava ethereumLog : logList) {
            logsString = logsString.concat(" " + ethereumLog.toString());
        }
        logsString = logsString.concat("}");

        String logsBloomStr;
        byte[] blooms = getLogsBloom();
        if (blooms != null) {
            logsBloomStr = BytesUtils.toHexString(getLogsBloom());
        } else {
            logsBloomStr="null";
        }

        return String.format(
                "EthereumReceipt (consensus data) { txType=%s, status=%d, cumGasUsed=%s, logs=%s, logsBloom=%s}",
                getTxType().toString(), status, getCumulativeGasUsed().toString(),
                logsString, logsBloomStr);
    }

    public String toString(Boolean withNonConsensusData) {
        String infoString = toString();
        if (withNonConsensusData) {

            String txHashStr;
            byte[] txHash = getTransactionHash();
            if (txHash != null) {
                txHashStr = BytesUtils.toHexString(txHash);
            } else {
                txHashStr="null";
            }

            String blockHashStr;
            byte[] blockHash = getBlockHash();
            if (blockHash != null) {
                blockHashStr = BytesUtils.toHexString(blockHash);
            } else {
                blockHashStr="null";
            }

            String contractAddressStr;
            byte[] cAddr = getContractAddress();
            if (cAddr != null) {
                contractAddressStr = BytesUtils.toHexString(cAddr);
            } else {
                contractAddressStr="null";
            }

            byte[] contractAddress;

            String infoNonConsensusStr = String.format(
                    " - (non consensus data) {txHash=%s, txIndex=%d, blockHash=%s, blockNumber=%d, gasUsed=%s, contractAddress=%s}",
                    txHashStr, getTransactionIndex(), blockHashStr, getBlockNumber(), getGasUsed().toString(), contractAddressStr);

            infoString = infoString.concat(infoNonConsensusStr);
        }

        return infoString;
    }
}

