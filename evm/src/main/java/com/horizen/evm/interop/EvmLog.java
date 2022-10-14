package com.horizen.evm.interop;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import com.horizen.evm.utils.Address;
import com.horizen.evm.utils.Converter;
import com.horizen.evm.utils.Hash;

import java.util.Arrays;

public class EvmLog {
    public Address address;

    @JsonSetter(nulls = Nulls.SKIP)
    public Hash[] topics = new Hash[0];
    @JsonSetter(nulls = Nulls.SKIP)
    public byte[] data = new byte[0];

    public EvmLog(Address address, Hash[] topics, byte[] data) {
        this.address = address;
        if (topics != null) {
            this.topics = topics;
        }
        if (data != null) {
            this.data = data;
        }
    }

    public EvmLog() {
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        var log = (EvmLog) o;
        if ((address == null) != (log.address == null)) return false;
        return ((address == null) || Arrays.equals(address.toBytes(), log.address.toBytes())) &&
            Arrays.equals(topics, log.topics) &&
            Arrays.equals(data, log.data);
    }

    @Override
    public int hashCode() {
        var result = 1;
        var addressBytes = (address == null) ? null : address.toBytes();
        result = result + Arrays.hashCode(addressBytes);
        if (topics != null && topics.length != 0) {
            for (var hash : topics) {
                var topic = (hash == null) ? null : hash.toBytes();
                result = 31 * result + Arrays.hashCode(topic);
            }
        } else {
            result = 31 * result + Arrays.hashCode(topics);
        }
        result = 31 * result + Arrays.hashCode(data);
        return result;
    }

    private String getTopicsString() {
        if (topics == null) return "null";
        var strs = Arrays
            .stream(topics)
            .map(topic -> topic == null ? "null" : Converter.toHexString(topic.toBytes()))
            .toArray(String[]::new);
        return String.format("[%s]", String.join(",", strs));
    }

    @Override
    public String toString() {
        return String.format(
            "EvmLog (log consensus data) {address=%s, topics=%s, data=%s}",
            address == null ? "null" : Converter.toHexString(address.toBytes()),
            getTopicsString(),
            data == null ? "null" : Converter.toHexString(data)
        );
    }
}
