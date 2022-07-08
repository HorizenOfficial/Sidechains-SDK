package com.horizen.evm.interop;

import com.horizen.evm.utils.Address;
import com.horizen.evm.utils.Hash;
import org.web3j.utils.Numeric;

public class EvmLog {
    public Address address;
    public Hash[] topics;
    public byte[] data;

    @Override
    public String toString() {
        String topicsStr = "topics{";

        for (int i = 0; i < topics.length; i++) {
            topicsStr = topicsStr.concat(" " + Numeric.toHexString(topics[i].toBytes()));
        }
        topicsStr = topicsStr.concat("}");

        return String.format(
                "EvmLog{address=%s, topics=%s, data=%s}",
                Numeric.toHexString(address.toBytes()),
                topicsStr,
                Numeric.toHexString(data));
    }
}
