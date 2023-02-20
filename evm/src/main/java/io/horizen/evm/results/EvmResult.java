package io.horizen.evm.results;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import io.horizen.evm.Address;

import java.math.BigInteger;
import java.util.Objects;

public class EvmResult {
    public final BigInteger usedGas;
    public final String evmError;
    public final byte[] returnData;
    public final Address contractAddress;
    public final Boolean reverted;
    public final JsonNode tracerResult;

    public EvmResult(
        @JsonProperty("usedGas") BigInteger usedGas,
        @JsonProperty("evmError") String evmError,
        @JsonProperty("returnData") byte[] returnData,
        @JsonProperty("contractAddress") Address contractAddress,
        @JsonProperty("reverted") Boolean reverted,
        @JsonProperty("tracerResult") JsonNode tracerResult
    ) {
        this.usedGas = usedGas;
        this.evmError = evmError;
        this.returnData = Objects.requireNonNullElse(returnData, new byte[0]);
        this.contractAddress = contractAddress;
        this.reverted = reverted;
        this.tracerResult = tracerResult;
    }
}
