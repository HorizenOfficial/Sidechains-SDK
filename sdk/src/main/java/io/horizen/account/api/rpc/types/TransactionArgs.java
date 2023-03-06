package io.horizen.account.api.rpc.types;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.horizen.account.api.rpc.handler.RpcException;
import io.horizen.account.api.rpc.utils.RpcCode;
import io.horizen.account.api.rpc.utils.RpcError;
import io.horizen.account.proposition.AddressProposition;
import io.horizen.account.state.Message;
import io.horizen.account.transaction.EthereumTransaction;
import io.horizen.account.utils.BigIntegerUtil;
import io.horizen.evm.Address;
import io.horizen.params.NetworkParams;
import org.web3j.utils.Numeric;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

@JsonIgnoreProperties(ignoreUnknown = true)
public class TransactionArgs {
    public final BigInteger type;
    public final Address from;
    public final Address to;
    // currently, gas cannot be final because the estimateGas algorithm needs to modify it
    public BigInteger gas;
    public final BigInteger gasPrice;
    public final BigInteger maxFeePerGas;
    public final BigInteger maxPriorityFeePerGas;
    public final BigInteger value;
    public final BigInteger nonce;

    // We accept "data" and "input" for backwards-compatibility reasons.
    // "input" is the newer name and should be preferred by clients.
    // Issue detail: https://github.com/ethereum/go-ethereum/issues/15628
    private final byte[] data;

    // Introduced by AccessListTxType transaction.
//    public final AccessList[] accessList;
    private final BigInteger chainId;

    public TransactionArgs(
        @JsonProperty("type") BigInteger type,
        @JsonProperty("from") Address from,
        @JsonProperty("to") Address to,
        @JsonProperty("gas") BigInteger gas,
        @JsonProperty("gasPrice") BigInteger gasPrice,
        @JsonProperty("maxFeePerGas") BigInteger maxFeePerGas,
        @JsonProperty("maxPriorityFeePerGas") BigInteger maxPriorityFeePerGas,
        @JsonProperty("value") BigInteger value,
        @JsonProperty("nonce") BigInteger nonce,
        @JsonProperty("data") byte[] data,
        @JsonProperty("input") byte[] input,
        @JsonProperty("chainId") BigInteger chainId
    ) throws RpcException {
        if (gasPrice != null && (maxFeePerGas != null || maxPriorityFeePerGas != null)) {
            throw new RpcException(RpcError.fromCode(
                RpcCode.InvalidParams,
                "both gasPrice and (maxFeePerGas or maxPriorityFeePerGas) specified"
            ));
        }
        // Sanity check the EIP-1559 fee parameters if present.
        if (gasPrice == null && maxFeePerGas != null && maxPriorityFeePerGas != null) {
            if (maxFeePerGas.compareTo(maxPriorityFeePerGas) < 0) {
                throw new RpcException(RpcError.fromCode(
                    RpcCode.InvalidParams,
                    String.format("maxFeePerGas (%s) < maxPriorityFeePerGas (%s)", maxFeePerGas, maxPriorityFeePerGas)
                ));
            }
        }
        if (data != null && input != null && !Arrays.equals(data, input)) {
            throw new RpcException(RpcError.fromCode(
                RpcCode.InvalidParams,
                "both \"data\" and \"input\" are set and not equal. Please use \"input\" to pass transaction call data"
            ));
        }
        this.type = type;
        this.from = from;
        this.to = to;
        this.gas = gas;
        this.gasPrice = gasPrice;
        this.maxFeePerGas = maxFeePerGas;
        this.maxPriorityFeePerGas = maxPriorityFeePerGas;
        this.value = Objects.requireNonNullElse(value, BigInteger.ZERO);
        this.nonce = nonce;
        this.data = Objects.requireNonNullElse(input != null ? input : data, new byte[0]);
        this.chainId = chainId;
        // sanity check for contract creation
        if (this.to == null && this.data.length == 0) {
            throw new RpcException(
                RpcError.fromCode(RpcCode.InvalidParams, "contract creation without any data provided"));
        }
    }

    public byte[] getData() {
        return data;
    }

    public String getDataString() {
        return Numeric.toHexString(data);
    }

    /**
     * Get sender address or use zero address if none specified.
     */
    public Address getFrom() {
        return from == null ? Address.ZERO : from;
    }

    public EthereumTransaction toTransaction(NetworkParams params) throws RpcException {
        var saneChainId = params.chainId();
        if (chainId != null && chainId.longValueExact() != saneChainId) {
            throw new RpcException(RpcError.fromCode(
                RpcCode.InvalidParams,
                String.format("invalid chainID: got %d, want %d", chainId, saneChainId)
            ));
        }
        var saneType = type == null ? 0 : type.intValueExact();
        var optionalToAddress = Optional.ofNullable(to == null ? null : new AddressProposition(to));

        switch (saneType) {
            case 0: // LEGACY type
                if (chainId != null) {
                    // eip155
                    return new EthereumTransaction(saneChainId, optionalToAddress, nonce, gasPrice, gas, value,
                        data, null
                    );
                } else {
                    return new EthereumTransaction(optionalToAddress, nonce, gasPrice, gas, value, data, null);
                }
            case 2: // EIP-1559
                return new EthereumTransaction(saneChainId, optionalToAddress, nonce, gas, maxPriorityFeePerGas,
                    maxFeePerGas, value, data, null
                );
            default:
                // unsupported type
                return null;
        }
    }

    /**
     * Converts the transaction arguments to the Message type used by the core.
     * This method is used in calls and traces that do not require a real live transaction.
     * Reimplementation of the same logic in GETH.
     */
    public Message toMessage(BigInteger baseFee, BigInteger rpcGasCap) throws RpcException {
        if (baseFee == null) {
            // Practically it's not possible. Because baseFee is always arrived from block header and every block header
            // has EIP-1559 support, so baseFee is never null.
            throw new IllegalArgumentException("baseFee must be not null.");
        }

        // global RPC gas cap
        var gasLimit = rpcGasCap;
        // cap gas limit given by the caller
        if (gas != null) {
            if (!BigIntegerUtil.isUint64(gas)) {
                throw new RpcException(RpcError.fromCode(RpcCode.InvalidParams, "invalid gas limit"));
            }
            if (gas.compareTo(gasLimit) < 0) {
                gasLimit = gas;
            }
        }
        var effectiveGasPrice = BigInteger.ZERO;
        var gasFeeCap = BigInteger.ZERO;
        var gasTipCap = BigInteger.ZERO;
        if (gasPrice != null) {
            // User specified the legacy gas field, convert to 1559 gas typing
            effectiveGasPrice = gasPrice;
            gasFeeCap = gasPrice;
            gasTipCap = gasPrice;
        } else {
            // User specified 1559 gas fields (or none), use those
            if (maxFeePerGas != null) {
                gasFeeCap = maxFeePerGas;
            }
            if (maxPriorityFeePerGas != null) {
                gasTipCap = maxPriorityFeePerGas;
            }
            // Backfill the legacy gasPrice for EVM execution, unless we're all zeroes
            if (gasFeeCap.bitLength() > 0 || gasTipCap.bitLength() > 0) {
                effectiveGasPrice = baseFee.add(gasTipCap).min(gasFeeCap);
            }
        }
        return new Message(getFrom(), to == null ? Optional.empty() : Optional.of(to), effectiveGasPrice, gasFeeCap,
            gasTipCap, gasLimit, value, nonce, getData(), true
        );
    }

    @Override
    public String toString() {
        return "TransactionArgs{" +
            "type=" + (type != null ? type.toString() : "empty") +
            ", from=" + (from != null ? from.toString() : "empty") +
            ", to=" + (to != null ? to.toString() : "empty") +
            ", gas=" + (gas != null ? gas.toString() : "empty") +
            ", gasPrice=" + (gasPrice != null ? gasPrice.toString() : "empty") +
            ", maxFeePerGas=" + (maxFeePerGas != null ? maxFeePerGas.toString() : "empty") +
            ", maxPriorityFeePerGas=" + (maxPriorityFeePerGas != null ? maxPriorityFeePerGas.toString() : "empty") +
            ", value=" + value.toString() +
            ", nonce=" + (nonce != null ? nonce.toString() : "empty") +
            ", data='" + getDataString() + "'" +
            ", chainId=" + (chainId != null ? chainId.toString() : "empty") +
            '}';
    }
}
