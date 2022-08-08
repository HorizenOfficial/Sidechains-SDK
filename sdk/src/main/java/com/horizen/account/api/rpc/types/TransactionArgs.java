package com.horizen.account.api.rpc.types;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.horizen.account.proposition.AddressProposition;
import com.horizen.account.state.Message;
import com.horizen.evm.utils.Address;
import org.web3j.utils.Numeric;

import java.math.BigInteger;

@JsonIgnoreProperties(ignoreUnknown = true)
public class TransactionArgs {
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
//    public BigInteger chainId;

    public byte[] getData() {
        var hex = input != null ? input : data;
        if (hex == null) return null;
        return Numeric.hexStringToByteArray(hex);
    }

    /**
     * Set sender address or use zero address if none specified.
     */
    public byte[] getFrom() {
        return from == null ? new byte[Address.LENGTH] : from.toBytes();
    }

    /**
     * Converts the transaction arguments to the Message type used by the core.
     * This method is used in calls and traces that do not require a real live transaction.
     * Reimplementation of the same logic in GETH.
     */
    public Message toMessage(BigInteger baseFee) {
        if (gasPrice != null && (maxFeePerGas != null || maxPriorityFeePerGas != null)) {
            throw new IllegalArgumentException("both gasPrice and (maxFeePerGas or maxPriorityFeePerGas) specified");
        }
        // global RPC gas cap (in geth this is a config variable)
        var gasLimit = BigInteger.valueOf(50_000_000);
        // cap gas limit given by the caller
        if (gas != null && gas.signum() > 0 && gas.compareTo(gasLimit) < 0) {
            gasLimit = gas;
        }
        var gasPrice = BigInteger.ZERO;
        var gasFeeCap = BigInteger.ZERO;
        var gasTipCap = BigInteger.ZERO;
        if (baseFee == null) {
            // If there's no basefee, then it must be a non-1559 execution
            if (this.gasPrice != null) {
                gasPrice = this.gasPrice;
                gasFeeCap = this.gasPrice;
                gasTipCap = this.gasPrice;
            }
        } else {
            // A basefee is provided, necessitating 1559-type execution
            if (this.gasPrice != null) {
                // User specified the legacy gas field, convert to 1559 gas typing
                gasPrice = this.gasPrice;
                gasFeeCap = this.gasPrice;
                gasTipCap = this.gasPrice;
            } else {
                // User specified 1559 gas fields (or none), use those
                if (maxFeePerGas != null) {
                    gasFeeCap = maxFeePerGas;
                }
                if (maxPriorityFeePerGas != null) {
                    gasFeeCap = maxPriorityFeePerGas;
                }
                // Backfill the legacy gasPrice for EVM execution, unless we're all zeroes
                if (gasFeeCap.bitLength() > 0 || gasTipCap.bitLength() > 0) {
                    gasPrice = baseFee.add(gasTipCap).min(gasFeeCap);
                }
            }
        }
        return new Message(
                new AddressProposition(getFrom()),
                to == null ? null : new AddressProposition(to.toBytes()),
                gasPrice,
                gasFeeCap,
                gasTipCap,
                gasLimit,
                value == null ? BigInteger.ZERO : value,
                nonce,
                getData()
        );
    }
}
