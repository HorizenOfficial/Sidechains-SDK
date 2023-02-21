package io.horizen.evm.results;

import com.fasterxml.jackson.databind.JsonNode;
import io.horizen.evm.Address;

import java.math.BigInteger;

public class EvmResult {
    public final BigInteger usedGas;
    public final String evmError;
    public final byte[] returnData;
    public final Address contractAddress;
    public final Boolean reverted;
    public final JsonNode tracerResult;

    public EvmResult(
        BigInteger usedGas,
        String evmError,
        byte[] returnData,
        Address contractAddress,
        Boolean reverted,
        JsonNode tracerResult
    ) {
        this.usedGas = usedGas;
        this.evmError = evmError;
        this.returnData = returnData;
        this.contractAddress = contractAddress;
        this.reverted = reverted;
        this.tracerResult = tracerResult;
    }
}
