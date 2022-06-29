package com.horizen.evm.interop;

import com.horizen.evm.utils.Address;
import com.horizen.evm.utils.Hash;

import java.math.BigInteger;

public class EvmContext {
    public Hash txHash;
    public int txIndex;
    public BigInteger difficulty;
    public Address coinbase;
    public BigInteger blockNumber;
    public BigInteger time;
    public BigInteger baseFee;
}
