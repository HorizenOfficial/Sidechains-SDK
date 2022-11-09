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
    public void nullEvmLogToStringTest() {
        EvmLog defaultLog = new EvmLog();
        assertEquals("EvmLog (log consensus data) {address=0000000000000000000000000000000000000000, topics=[], data=}", defaultLog.toString());

        EvmLog nullLog = new EvmLog();
        nullLog.setData(null);
        nullLog.setTopics(null);
        assertEquals("EvmLog (log consensus data) {address=0000000000000000000000000000000000000000, topics=[], data=}", nullLog.toString());

        EvmLog invalidTopicsLog = new EvmLog();
        invalidTopicsLog.setTopics(new Hash[2]);
        assertEquals(
                "EvmLog (log consensus data) {address=0000000000000000000000000000000000000000, topics=[null,null], data=}", invalidTopicsLog.toString());
    }

    @Test
    public void nullEvmLogHashCodeTest() {
        EvmLog defaultLog = new EvmLog();
        EvmLog defaultLog2 = new EvmLog();
        assertEquals(defaultLog, defaultLog2);
        assertEquals(defaultLog.hashCode(), defaultLog2.hashCode());

        defaultLog.setData(null);
        defaultLog.setTopics(null);
        assertEquals(defaultLog, defaultLog2);
        assertEquals(defaultLog.hashCode(), defaultLog2.hashCode());
        defaultLog2.setData(null);
        defaultLog2.setTopics(null);
        assertEquals(defaultLog, defaultLog2);
        assertEquals(defaultLog.hashCode(), defaultLog2.hashCode());

        defaultLog.setTopics(new Hash[0]);
        assertEquals(defaultLog, defaultLog2);
        assertEquals(defaultLog.hashCode(), defaultLog2.hashCode());
        defaultLog2.setTopics(new Hash[0]);
        assertEquals(defaultLog, defaultLog2);
        assertEquals(defaultLog.hashCode(), defaultLog2.hashCode());

        defaultLog.setTopics(null);
        defaultLog.setData(new byte[2]);
        defaultLog2.setData(null);
        defaultLog2.setTopics(null);
        assertNotEquals(defaultLog, defaultLog2);
        assertNotEquals(defaultLog.hashCode(), defaultLog2.hashCode());
        defaultLog2.setData(new byte[2]);
        assertEquals(defaultLog, defaultLog2);
        assertEquals(defaultLog.hashCode(), defaultLog2.hashCode());

        EvmLog randomLog = new EvmLog();
        var addressBytes = new byte[Address.LENGTH];
        new Random().nextBytes(addressBytes);
        randomLog.setAddress(Address.fromBytes(addressBytes));
        var topics = new Hash[4];
        topics[0] = Hash.fromBytes(
                Converter.fromHexString("0000000000000000000000000000000000000000000000000000000000000000"));
        topics[1] = Hash.fromBytes(
                Converter.fromHexString("1111111111111111111111111111111111111111111111111111111111111111"));
        topics[2] = Hash.fromBytes(
                Converter.fromHexString("2222222222222222222222222222222222222222222222222222222222222222"));
        topics[3] = Hash.fromBytes(
                Converter.fromHexString("3333333333333333333333333333333333333333333333333333333333333333"));

        randomLog.setTopics(topics);
        var data = Converter.fromHexString("aabbccddeeff22");
        randomLog.setData(data);

        EvmLog randomLog2 = new EvmLog(Address.fromBytes(addressBytes), topics, data);
        assertEquals(randomLog, randomLog2);
        assertEquals(randomLog.hashCode(), randomLog2.hashCode());
    }
}
