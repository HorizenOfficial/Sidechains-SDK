package com.horizen.evm.interop;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.horizen.evm.utils.Address;
import com.horizen.evm.utils.Converter;
import com.horizen.evm.utils.Hash;

import java.util.Arrays;
import java.util.Objects;

public class EvmLog {
    public final Address address;
    public final Hash[] topics;
    public final byte[] data;

    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    public EvmLog(
        @JsonProperty("address") Address address,
        @JsonProperty("topics") Hash[] topics,
        @JsonProperty("data") byte[] data
    ) {
        this.address = address;
        this.topics = topics;
        this.data = Objects.requireNonNullElseGet(data, () -> new byte[0]);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        var log = (EvmLog) o;
        return Arrays.equals(address.toBytes(), log.address.toBytes()) &&
            Arrays.equals(topics, log.topics) &&
            Arrays.equals(data, log.data);
    }

    @Override
    public int hashCode() {
        var result = 1;
        var addressBytes = address.toBytes();
        result = result + Arrays.hashCode(addressBytes);
        if (topics.length != 0) {
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
        var strs = Arrays
            .stream(topics)
            .map(topic -> Converter.toHexString(topic.toBytes()))
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
