package com.horizen.evm.interop;

import com.horizen.evm.EvmContext;
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
    public Integer blockHashCallbackHandle;

    public EvmParams(
        int handle,
        Address from,
        Address to,
        BigInteger value,
        byte[] input,
        BigInteger availableGas,
        BigInteger gasPrice,
        EvmContext context,
        TraceOptions traceOptions,
        Integer blockHashCallbackHandle
    ) {
        super(handle);
        this.from = from;
        this.to = to;
        this.value = value;
        this.input = input;
        this.availableGas = availableGas;
        this.gasPrice = gasPrice;
        this.context = context;
        this.traceOptions = traceOptions;
        this.blockHashCallbackHandle = blockHashCallbackHandle;
    }
}
