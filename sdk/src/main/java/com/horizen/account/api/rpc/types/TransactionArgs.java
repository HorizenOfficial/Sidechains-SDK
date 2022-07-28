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

    public byte[] getFrom() {
        return from == null ? new byte[Address.LENGTH] : from.toBytes();
    }

    public Message toMessage() {
        return new Message(
                new AddressProposition(getFrom()),
                new AddressProposition(to.toBytes()),
                gasPrice,
                maxFeePerGas,
                maxPriorityFeePerGas,
                gas,
                value,
                nonce,
                getData()
        );
    }
}
