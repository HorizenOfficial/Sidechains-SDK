package com.horizen.account.receipt;

import com.horizen.evm.TrieHasher;
import com.horizen.evm.interop.EvmLog;
import com.horizen.evm.utils.Address;
import com.horizen.evm.utils.Hash;
import com.horizen.utils.BytesUtils;
import org.junit.Test;
import org.web3j.utils.Numeric;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static java.util.Map.entry;
import static org.junit.Assert.assertEquals;

public class EthereumLogsTest {

    @Test
    public void receiptSimpleEncodeDecodeTest() {

        EvmLog evmLog = new EvmLog();
        evmLog.address = Address.FromBytes(BytesUtils.fromHexString("1122334455667788990011223344556677889900"));
        evmLog.topics = new Hash[4];
        evmLog.topics[0] = Hash.FromBytes(BytesUtils.fromHexString("0000000000000000000000000000000000000000000000000000000000000000"));
        evmLog.topics[1] = Hash.FromBytes(BytesUtils.fromHexString("1111111111111111111111111111111111111111111111111111111111111111"));
        evmLog.topics[2] = Hash.FromBytes(BytesUtils.fromHexString("2222222222222222222222222222222222222222222222222222222222222222"));
        evmLog.topics[3] = Hash.FromBytes(BytesUtils.fromHexString("3333333333333333333333333333333333333333333333333333333333333333"));
        evmLog.data = BytesUtils.fromHexString("aabbccddeeff");

        EthereumLog ethereumLog = new EthereumLog(evmLog);
        System.out.println(ethereumLog);


        byte[] encodedLog = EthereumLog.rlpEncode(ethereumLog);
        System.out.println(BytesUtils.toHexString(encodedLog));

        // read what you write
        byte[] dataBytes = encodedLog;
        EthereumLog decodedLog = EthereumLog.rlpDecode(dataBytes);
        System.out.println(decodedLog);

        assertEquals(
                BytesUtils.toHexString(ethereumLog.getConsensusLogData().address.toBytes()),
                BytesUtils.toHexString(decodedLog.getConsensusLogData().address.toBytes()));

    }
}
