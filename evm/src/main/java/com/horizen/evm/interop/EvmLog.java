package com.horizen.evm.interop;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.horizen.evm.utils.Address;
import com.horizen.evm.utils.Converter;
import com.horizen.evm.utils.Hash;

import java.util.Arrays;

public class EvmLog {
    private Address address = Address.addressZero();
    private Hash[] topics = new Hash[0];
    private byte[] data = new byte[0];

    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    public EvmLog(@JsonProperty("address") Address address, @JsonProperty("topics") Hash[] topics, @JsonProperty("data") byte[] data) {

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
        if ((address.toBytes() == Address.addressZero().toBytes()) != (log.address.toBytes() == Address.addressZero().toBytes())) return false;
        return ((address.toBytes() == Address.addressZero().toBytes()) || Arrays.equals(address.toBytes(), log.address.toBytes())) &&
                Arrays.equals(topics, log.topics) &&
                Arrays.equals(data, log.data);
    }

    @Override
    public int hashCode() {
        var result = 1;
        var addressBytes = address.toBytes();
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

    public Hash[] getTopics() {
        return topics;
    }

    public void setTopics(Hash[] topics) {
        this.topics = topics == null ? new Hash[0] : topics;
    }

    public byte[] getData() {
        return data;
    }

    public void setData(byte[] data) {
        this.data = data == null ? new byte[0] : data;
    }

    public Address getAddress() {
        return address;
    }

    public void setAddress(Address address) {
        this.address = address;
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
                Converter.toHexString(address.toBytes()),
                getTopicsString(),
                Converter.toHexString(data)
        );
    }
}
