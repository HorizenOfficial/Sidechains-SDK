package com.horizen.evm.library;

import com.horizen.evm.utils.Address;

public class EvmResult {
    public byte[] returnData;
    public Address contractAddress;
    public long leftOverGas;
    public String evmError;
}
