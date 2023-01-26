package com.horizen.evm.interop;

import com.fasterxml.jackson.databind.JsonNode;
import com.horizen.evm.utils.Address;

import java.math.BigInteger;

public class EvmResult {
    public BigInteger usedGas;
    public String evmError;
    public byte[] returnData;
    public Address contractAddress;
    public Boolean reverted;
    public JsonNode tracerResult;

}
