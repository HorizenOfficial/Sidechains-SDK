package com.horizen.evm.library;

import com.horizen.evm.utils.Address;
import com.horizen.evm.utils.Hash;
import com.horizen.evm.utils.HexBytes;

import java.math.BigInteger;

public class EvmLog {
    public Address address;
    public Hash[] topics;
    public HexBytes data;
    public Hash blockHash;
    public BigInteger blockNumber;
    public Hash transactionHash;
    public BigInteger transactionIndex;
    public BigInteger logIndex;
    public boolean removed;
}
