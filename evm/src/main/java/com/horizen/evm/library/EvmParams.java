package com.horizen.evm.library;

public class EvmParams extends HandleParams {
    public static class EvmConfig {
        public String difficulty; // uint256
        public String origin; // address
        public String coinbase; // address
        public String blockNumber; // uint256
        public String time; // uint256
        public long gasLimit; // uint64
        public String gasPrice; // uint256
        public String value; // uint256
        public String baseFee; // uint256
    }

    public EvmConfig config;
    public String address;
    public byte[] input;

    public EvmParams() {
    }

    public EvmParams(int handle, EvmConfig config, String address, byte[] input) {
        super(handle);
        this.config = config;
        this.address = address;
        this.input = input;
    }
}
