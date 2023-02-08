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
        return address.equals(log.address) &&
            Arrays.equals(topics, log.topics) &&
            Arrays.equals(data, log.data);
    }

    @Override
    public int hashCode() {
        var result = 1;
        result = result + address.hashCode();
        if (topics.length != 0) {
            for (var hash : topics) {
                result = 31 * result + ((hash == null) ? 0 : hash.hashCode());
            }
        } else {
            result = 31 * result + Arrays.hashCode(topics);
        }
        result = 31 * result + Arrays.hashCode(data);
        return result;
    }

    @Override
    public String toString() {
        return String.format(
            "EvmLog (log consensus data) {address=%s, topics=%s, data=%s}",
            address,
            Arrays.toString(topics),
            Converter.toHexString(data)
        );
    }
}
