package com.horizen.evm.interop;

import com.horizen.evm.utils.Address;
import com.horizen.evm.utils.Hash;

public class EvmLog {
    public Address address;
    public Hash[] topics;
    public byte[] data;
}
