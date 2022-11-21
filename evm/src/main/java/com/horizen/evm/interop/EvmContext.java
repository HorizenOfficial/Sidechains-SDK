package com.horizen.evm.interop;

import com.horizen.evm.utils.Address;
import com.horizen.evm.utils.Hash;

import java.math.BigInteger;

public class EvmContext {
    public long chainID;
    public Address coinbase;
    public BigInteger gasLimit;
    public BigInteger blockNumber;
    public BigInteger time;
    public BigInteger baseFee;
    public Hash random;
}
