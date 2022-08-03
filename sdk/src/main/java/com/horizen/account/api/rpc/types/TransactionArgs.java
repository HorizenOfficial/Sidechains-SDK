package com.horizen.account.api.rpc.types;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.horizen.account.transaction.EthereumTransaction;
import com.horizen.evm.utils.Address;
import org.web3j.crypto.Sign;
import org.web3j.utils.Numeric;

import java.math.BigInteger;

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
        var hex = input != null ? input : data;
        if (hex == null) return null;
        return Numeric.hexStringToByteArray(hex);
    }

    public String getDataString() {
        return input != null ? input : data;
    }

    public byte[] getFrom() {
        return from == null ? new byte[Address.LENGTH] : from.toBytes();
    }

    public EthereumTransaction toTransaction() {
        Sign.SignatureData prepared = null;
        if (chainId != null)
            prepared = new Sign.SignatureData(BigInteger.valueOf(chainId.longValue()).toByteArray(), new byte[]{0}, new byte[]{0});
        if (type == null || type.equals(BigInteger.ZERO) /* Legacy */) {
            return new EthereumTransaction(
                    to != null ? to.toUTXOString() : null, nonce, gasPrice, gas, value, this.getDataString(), prepared
            );
        } else if (type.equals(BigInteger.ONE)  /* TODO EIP-2... */) {
            return new EthereumTransaction(
                    to != null ? to.toUTXOString() : null, nonce, gasPrice, gas, value, this.getDataString(), prepared
            );
        } else if (type.equals(BigInteger.TWO) /* EIP-1559 */) {
            assert chainId != null;
            return new EthereumTransaction(
                    chainId.longValue(),
                    to != null ? to.toUTXOString() : null,
                    nonce,
                    gas,
                    maxPriorityFeePerGas,
                    maxFeePerGas,
                    value,
                    this.getDataString(),
                    null
            );
        }
        return null;
    }
}
