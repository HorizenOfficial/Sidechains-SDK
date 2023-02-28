package io.horizen.account.state;

import io.horizen.evm.Address;
import io.horizen.utils.BytesUtils;
import org.web3j.utils.Numeric;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Optional;

public class Message {
    private final Address from;
    private final Optional<Address> to;

    private final BigInteger gasPrice;
    private final BigInteger gasFeeCap;
    private final BigInteger gasTipCap;
    private final BigInteger gasLimit;

    private final BigInteger value;
    private final BigInteger nonce;
    private final byte[] data;

    private final boolean fakeMsg;

    public Message(
        Address from,
        Optional<Address> to,
        BigInteger gasPrice,
        BigInteger gasFeeCap,
        BigInteger gasTipCap,
        BigInteger gasLimit,
        BigInteger value,
        BigInteger nonce,
        byte[] data,
        boolean fakeMsg
    ) {
        this.from = from;
        this.to = to;
        this.gasPrice = gasPrice;
        this.gasFeeCap = gasFeeCap;
        this.gasTipCap = gasTipCap;
        this.gasLimit = gasLimit;
        this.value = value;
        this.nonce = nonce;
        this.data = data == null ? null : Arrays.copyOf(data, data.length);
        this.fakeMsg = fakeMsg;
    }

    public Address getFrom() {
        return from;
    }

    public Optional<Address> getTo() {
        return to;
    }

    public BigInteger getGasPrice() {
        return gasPrice;
    }

    public BigInteger getGasFeeCap() {
        return gasFeeCap;
    }

    public BigInteger getGasTipCap() {
        return gasTipCap;
    }

    public BigInteger getGasLimit() {
        return gasLimit;
    }

    public BigInteger getValue() {
        return value;
    }

    public BigInteger getNonce() {
        return nonce;
    }

    public byte[] getData() {
        return data;
    }

    public boolean getIsFakeMsg() { return fakeMsg; }

    public String toString() {
        return String.format(
            "Message{from=%s, to=%s, gasPrice=%s, gasFeeCap=%s, gasTipCap=%s, gasLimit=%s, value=%s, nonce=%s, data=%s, fakeMsg=%s}",
            from.toString(),
            to.map(Address::toString).orElse(""),
            gasPrice != null ? Numeric.toHexStringWithPrefix(gasPrice) : "null",
            gasFeeCap != null ? Numeric.toHexStringWithPrefix(gasFeeCap) : "null",
            gasTipCap != null ? Numeric.toHexStringWithPrefix(gasTipCap) : "null",
            gasLimit != null ? Numeric.toHexStringWithPrefix(gasLimit) : "null",
            value != null ? Numeric.toHexStringWithPrefix(value) : "null",
            nonce != null ? Numeric.toHexStringWithPrefix(nonce) : "null",
            data != null ? BytesUtils.toHexString(data) : "null",
            fakeMsg ? "YES" : "NO"
        );
    }
}
