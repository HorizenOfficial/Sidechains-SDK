package com.horizen.evm.interop;

import com.horizen.evm.LibEvmTestBase;
import com.horizen.evm.utils.Address;
import com.horizen.evm.utils.Converter;
import com.horizen.evm.utils.Hash;
import org.junit.Test;

import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class EvmLogTest extends LibEvmTestBase {

    @Test
    public void nullEvmLogHashCodeTest() {
        var addressBytes = new byte[Address.LENGTH];
        new Random().nextBytes(addressBytes);
        var topics = new Hash[4];
        topics[0] = Hash.fromBytes(
                Converter.fromHexString("0000000000000000000000000000000000000000000000000000000000000000"));
        topics[1] = Hash.fromBytes(
                Converter.fromHexString("1111111111111111111111111111111111111111111111111111111111111111"));
        topics[2] = Hash.fromBytes(
                Converter.fromHexString("2222222222222222222222222222222222222222222222222222222222222222"));
        topics[3] = Hash.fromBytes(
                Converter.fromHexString("3333333333333333333333333333333333333333333333333333333333333333"));

        var data = Converter.fromHexString("aabbccddeeff22");

        EvmLog randomLog = new EvmLog(Address.fromBytes(addressBytes), topics, data);

        EvmLog randomLog2 = new EvmLog(Address.fromBytes(addressBytes), topics, data);
        assertEquals(randomLog, randomLog2);
        assertEquals(randomLog.hashCode(), randomLog2.hashCode());
    }
}
