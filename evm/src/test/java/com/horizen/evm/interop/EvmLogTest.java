package com.horizen.evm.interop;

import com.horizen.evm.LibEvmTestBase;
import com.horizen.evm.utils.Address;
import com.horizen.evm.utils.Converter;
import com.horizen.evm.utils.Hash;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class EvmLogTest extends LibEvmTestBase {

    @Test
    public void nullEvmLogHashCodeTest() {
        var address = new Address("0x670f5b2c2a839622103b176334f07c1e0bcf6cc5");

        var topics = new Hash[] {
            new Hash("0x0000000000000000000000000000000000000000000000000000000000000000"),
            new Hash("0x1111111111111111111111111111111111111111111111111111111111111111"),
            new Hash("0x2222222222222222222222222222222222222222222222222222222222222222"),
            new Hash("0x3333333333333333333333333333333333333333333333333333333333333333"),
        };

        var data = Converter.fromHexString("aabbccddeeff22");

        EvmLog randomLog = new EvmLog(address, topics, data);
        EvmLog randomLog2 = new EvmLog(address, topics, data);
        assertEquals(randomLog, randomLog2);
        assertEquals(randomLog.hashCode(), randomLog2.hashCode());
    }
}
