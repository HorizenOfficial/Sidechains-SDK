package com.horizen.evm.interop;

import com.horizen.evm.utils.Address;

import java.math.BigInteger;

public class EvmParams extends HandleParams {
    public Address from;
    public Address to;
    public BigInteger value;
    public byte[] input;
    public BigInteger gas; // uint64
    public BigInteger gasPrice;

    public EvmContext context;

    public EvmParams() {
    }

    public EvmParams(
            int handle,
            byte[] from,
            byte[] to,
            BigInteger value,
            byte[] input,
            BigInteger gas,
            BigInteger gasPrice,
            EvmContext context
    ) {
        super(handle);
        this.from = Address.FromBytes(from);
        this.to = Address.FromBytes(to);
        this.value = value;
        this.input = input;
        this.gas = gas;
        this.gasPrice = gasPrice;
        this.context = context;
    }
}
