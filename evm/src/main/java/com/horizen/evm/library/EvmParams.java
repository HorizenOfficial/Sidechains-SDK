package com.horizen.evm.library;

import com.horizen.evm.utils.Address;

import java.math.BigInteger;

public class EvmParams extends HandleParams {
    public static class EvmConfig {
        public BigInteger difficulty; // uint256
        public Address origin; // address
        public Address coinbase; // address
        public BigInteger blockNumber; // uint256
        public BigInteger time; // uint256
        public long gasLimit; // uint64
        public BigInteger gasPrice; // uint256
        public BigInteger value; // uint256
        public BigInteger baseFee; // uint256
    }

    public EvmConfig config;
    public Address address;
    public byte[] input;

    public EvmParams() {
    }

    public EvmParams(int handle, EvmConfig config, byte[] address, byte[] input) {
        super(handle);
        this.config = config;
        if (address != null) {
            this.address = new Address(address);
        }
        this.input = input;
    }
}
