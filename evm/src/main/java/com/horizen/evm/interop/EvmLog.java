package com.horizen.evm.interop;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import com.horizen.evm.utils.Address;
import com.horizen.evm.utils.Converter;
import com.horizen.evm.utils.Hash;

import java.util.Arrays;
import java.util.Objects;

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
        EvmLog log = (EvmLog) o;

        if ((address == null && log.address != null) ||
                (log.address == null && address != null))
            return false;
        boolean areEqual = (address == null) || Arrays.equals(address.toBytes(), log.address.toBytes());
        areEqual = areEqual &&
                Arrays.equals(topics, log.topics) &&
                Arrays.equals(data, log.data);
        return areEqual;
    }

    @Override
    public int hashCode() {
        int result = 1;
        byte[] addressBytes = (address == null) ? null : address.toBytes();
        result = result + Arrays.hashCode(addressBytes);
        if (topics != null && topics.length != 0){
            for (int i = 0; i < topics.length; i++) {
                byte[] topic = (topics[i] == null) ? null : topics[i].toBytes();
                result = 31 * result + Arrays.hashCode(topic);
            }
        }
        else {
            result = 31 * result + Arrays.hashCode(topics);
        }
        result = 31 * result + Arrays.hashCode(data);
        return result;
    }


    @Override
    public String toString() {
        String addressString = (address == null) ? "null" : Converter.toHexString(address.toBytes());


        String topicsStr = "topics{";

        if (topics != null) {
            for (int i = 0; i < topics.length; i++) {
                String topic = (topics[i] == null) ? "null" : Converter.toHexString(topics[i].toBytes());
                topicsStr = topicsStr.concat(" " + topic);
            }
        }
        else
            topicsStr = topicsStr.concat("null");
        topicsStr = topicsStr.concat("}");

        String dataString = (data == null) ? "null" : Converter.toHexString(data);
        return String.format(
                "EvmLog (log consensus data) {address=%s, topics=%s, data=%s}",
                addressString,
                topicsStr,
                dataString);
    }
}
