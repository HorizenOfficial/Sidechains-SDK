package com.horizen.account.receipt;

import com.horizen.evm.interop.EvmLog;
import com.horizen.evm.utils.Address;
import com.horizen.evm.utils.Hash;
import com.horizen.utils.BytesUtils;
import org.junit.Test;
import scorex.crypto.hash.Keccak256;

import static org.junit.Assert.assertEquals;

public class EthereumLogsTestJava {

    public static EthereumLogJava createTestEthereumLogJava() {
        EvmLog evmLog = new EvmLog();
        evmLog.address = Address.FromBytes(BytesUtils.fromHexString("1122334455667788990011223344556677889900"));
        evmLog.topics = new Hash[4];
        evmLog.topics[0] = Hash.FromBytes(BytesUtils.fromHexString("0000000000000000000000000000000000000000000000000000000000000000"));
        evmLog.topics[1] = Hash.FromBytes(BytesUtils.fromHexString("1111111111111111111111111111111111111111111111111111111111111111"));
        evmLog.topics[2] = Hash.FromBytes(BytesUtils.fromHexString("2222222222222222222222222222222222222222222222222222222222222222"));
        evmLog.topics[3] = Hash.FromBytes(BytesUtils.fromHexString("3333333333333333333333333333333333333333333333333333333333333333"));
        evmLog.data = BytesUtils.fromHexString("aabbccddeeff");

        // add also non consensus data info, not rlp handled
        EthereumLogJava log = new EthereumLogJava(evmLog);
        log.setTransactionHash((byte[]) Keccak256.hash("txhash".getBytes()));
        log.setTransactionIndex(1);
        log.setBlockHash((byte[]) Keccak256.hash("blockhash".getBytes()));
        log.setBlockNumber(100);
        log.setLogIndex(1);
        log.setRemoved(0);

        return log;

    }

    @Test
    public void receiptSimpleEncodeDecodeTest() {

        EthereumLogJava ethereumLog = createTestEthereumLogJava();
        System.out.println(ethereumLog);


        byte[] encodedLog = EthereumLogJava.rlpEncode(ethereumLog);
        System.out.println(BytesUtils.toHexString(encodedLog));

        // read what you write
        byte[] dataBytes = encodedLog;
        EthereumLogJava decodedLog = EthereumLogJava.rlpDecode(dataBytes);
        System.out.println(decodedLog);

        assertEquals(
                BytesUtils.toHexString(ethereumLog.getConsensusLogData().address.toBytes()),
                BytesUtils.toHexString(decodedLog.getConsensusLogData().address.toBytes()));

    }
}
