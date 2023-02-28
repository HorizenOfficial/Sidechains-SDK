package io.horizen.account.api.rpc.types;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.horizen.account.api.rpc.handler.RpcException;
import com.horizen.account.api.rpc.utils.RpcCode;
import com.horizen.account.api.rpc.utils.RpcError;
import com.horizen.account.proposition.AddressProposition;
import com.horizen.account.state.Message;
import com.horizen.account.transaction.EthereumTransaction;
import com.horizen.account.utils.BigIntegerUtil;
import com.horizen.account.utils.EthereumTransactionUtils;
import io.horizen.evm.Address;
import com.horizen.params.NetworkParams;
import org.web3j.utils.Numeric;

import java.math.BigInteger;
import java.util.Optional;

@JsonIgnoreProperties(ignoreUnknown = true)
public class TransactionArgs {
    public BigInteger type;
    public Address from;
    public Address to;
    public BigInteger gas;
    public BigInteger gasPrice;
    public BigInteger maxFeePerGas;
    public BigInteger maxPriorityFeePerGas;
    public BigInteger value;
    public BigInteger nonce;

    // We accept "data" and "input" for backwards-compatibility reasons.
    // "input" is the newer name and should be preferred by clients.
    // Issue detail: https://github.com/ethereum/go-ethereum/issues/15628
    public String data;
    public String input;

    // Introduced by AccessListTxType transaction.
//    public AccessList[] accessList;
    public BigInteger chainId;

    public byte[] getData() {
        var hex = getDataString();
        if (hex == null) return null;
        return Numeric.hexStringToByteArray(hex);
    }

    public String getDataString() {
        return input != null ? input : data;
    }

    /**
     * Set sender address or use zero address if none specified.
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
        var dataBytes = EthereumTransactionUtils.getDataFromString(this.getDataString());

        switch (saneType) {
            case 0: // LEGACY type
                if (chainId != null) {
                    // eip155
                    return new EthereumTransaction(
                        saneChainId, optionalToAddress,
                        nonce, gasPrice, gas, value, dataBytes, null
                    );
                } else {
                    return new EthereumTransaction(
                        optionalToAddress,
                        nonce, gasPrice, gas, value, dataBytes, null
                    );
                }
            case 2: // EIP-1559
                return new EthereumTransaction(
                    saneChainId, optionalToAddress,
                    nonce, gas, maxPriorityFeePerGas, maxFeePerGas, value, dataBytes, null
                );
            default:
                // unsupported type
                return null;
        }
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
            ", value=" + (value != null ? value.toString() : "empty") +
            ", nonce=" + (nonce != null ? nonce.toString() : "empty") +
            ", data='" + (data != null ? data : "empty") + '\'' +
            ", input='" + (input != null ? input : "empty") + '\'' +
            ", chainId=" + (chainId != null ? chainId.toString() : "empty") +
            '}';
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

        if (gasPrice != null && (maxFeePerGas != null || maxPriorityFeePerGas != null)) {
            throw new RpcException(RpcError.fromCode(
                RpcCode.InvalidParams,
                "both gasPrice and (maxFeePerGas or maxPriorityFeePerGas) specified"
            ));
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
        return new Message(
            getFrom(),
            to == null ? Optional.empty() : Optional.of(to),
            effectiveGasPrice,
            gasFeeCap,
            gasTipCap,
            gasLimit,
            value == null ? BigInteger.ZERO : value,
            nonce,
            getData(),
            true
        );
    }
}
