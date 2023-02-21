package com.horizen.evm.results;

import com.horizen.evm.Address;
import com.horizen.evm.Hash;

import java.util.Objects;

public class EvmLog {
    public final Address address;
    public final Hash[] topics;
    public final byte[] data;

    public EvmLog(Address address, Hash[] topics, byte[] data) {
        this.address = address;
        this.topics = topics;
        this.data = Objects.requireNonNullElseGet(data, () -> new byte[0]);
    }
}
