package com.horizen.evm.interop;

import com.horizen.evm.utils.Address;
import com.horizen.evm.utils.Converter;
import com.horizen.evm.utils.Hash;
import java.util.Arrays;

public class EvmLog {
    public Address address;
    public Hash[] topics;
    public byte[] data;

    public EvmLog (Address address, Hash[] topics, byte[] data) {
        this.address = address;
        this.topics = topics;
        this.data = data;
    }

    public EvmLog () { }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EvmLog log = (EvmLog) o;

        return Arrays.equals(address.toBytes(), log.address.toBytes()) &&
                Arrays.equals(topics, log.topics) &&
                Arrays.equals(data, log.data);
    }

    @Override
    public int hashCode() {
        int result = Arrays.hashCode(address.toBytes());
        for (int i = 0; i < topics.length; i++)
          result = 31 * result + Arrays.hashCode(topics[i].toBytes());
        result = 31 * result + Arrays.hashCode(data);
        return result;
    }

    @Override
    public String toString() {
        String topicsStr = "topics{";

        for (int i = 0; i < topics.length; i++) {
            topicsStr = topicsStr.concat(" " + Converter.toHexString(topics[i].toBytes()));
        }
        topicsStr = topicsStr.concat("}");

        return String.format(
                "EvmLog (log consensus data) {address=%s, topics=%s, data=%s}",
                Converter.toHexString(address.toBytes()),
                topicsStr,
                Converter.toHexString(data));
    }
}
