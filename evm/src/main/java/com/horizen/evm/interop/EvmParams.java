package com.horizen.evm.interop;

import com.horizen.evm.utils.Address;

import java.math.BigInteger;

public class EvmParams extends HandleParams {
    public Address from;
    public Address to;
    public BigInteger value;
    public byte[] input;
    public BigInteger availableGas; // uint64
    public BigInteger gasPrice;

    public EvmContext context;

    public TraceOptions traceOptions;

    public EvmParams(
            int handle,
            byte[] from,
            byte[] to,
            BigInteger value,
            byte[] input,
            BigInteger availableGas,
            BigInteger gasPrice,
            EvmContext context,
            TraceOptions traceOptions
    ) {
        super(handle);
        this.from = Address.fromBytes(from);
        this.to = Address.fromBytes(to);
        this.value = value;
        this.input = input;
        this.availableGas = availableGas;
        this.gasPrice = gasPrice;
        this.context = context;
        this.traceOptions = traceOptions;
    }
}
