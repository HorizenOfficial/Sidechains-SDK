package io.horizen.evm.results;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.horizen.evm.Address;
import io.horizen.evm.Hash;

import java.util.Objects;

public class EvmLog {
    public final Address address;
    public final Hash[] topics;
    public final byte[] data;

    public EvmLog(
        @JsonProperty("address") Address address,
        @JsonProperty("topics") Hash[] topics,
        @JsonProperty("data") byte[] data
    ) {
        this.address = address;
        this.topics = Objects.requireNonNullElse(topics, new Hash[0]);
        this.data = Objects.requireNonNullElse(data, new byte[0]);
    }
}
