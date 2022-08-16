package com.horizen.evm.interop;

import com.horizen.evm.utils.Address;

import java.math.BigInteger;

public class EvmContext {
    public BigInteger difficulty;
    public Address coinbase;
    public BigInteger blockNumber;
    public BigInteger time;
    public BigInteger baseFee;
    public BigInteger gasLimit;
}
