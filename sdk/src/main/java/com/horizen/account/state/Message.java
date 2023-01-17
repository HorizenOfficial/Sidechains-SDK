package com.horizen.account.state;

import com.horizen.account.proposition.AddressProposition;
import com.horizen.evm.utils.Address;
import com.horizen.utils.BytesUtils;
import org.web3j.utils.Numeric;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Optional;

public class Message {
    private final Optional<AddressProposition> from;
    private final Optional<AddressProposition> to;

    private final BigInteger gasPrice;
    private final BigInteger gasFeeCap;
    private final BigInteger gasTipCap;
    private final BigInteger gasLimit;

    private final BigInteger value;
    private final BigInteger nonce;
    private final byte[] data;

    private final boolean fakeMsg;

    public Message(
            Optional<AddressProposition> from,
            Optional<AddressProposition> to,
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

    public Optional<AddressProposition> getFrom() {
        return from;
    }

    public byte[] getFromAddressBytes() {
        return from.map(AddressProposition::address).orElseGet(() -> new byte[Address.LENGTH]);
    }

    public Optional<AddressProposition> getTo() {
        return to;
    }

    public byte[] getToAddressBytes() { return to.isEmpty() ? null : to.get().address(); }

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
                getFrom().isEmpty() ? "" : BytesUtils.toHexString(getFrom().get().address()),
                getTo().isEmpty() ? "" : BytesUtils.toHexString(getTo().get().address()),
                getGasPrice() != null ?  Numeric.toHexStringWithPrefix(getGasPrice()) : "null",
                getGasFeeCap() != null ? Numeric.toHexStringWithPrefix(getGasFeeCap()) : "null",
                getGasTipCap() != null ? Numeric.toHexStringWithPrefix(getGasTipCap()) : "null",
                getGasLimit() != null ? Numeric.toHexStringWithPrefix(getGasLimit()) : "null",
                getValue() != null ? Numeric.toHexStringWithPrefix(getValue()) : "null",
                getNonce() != null ? Numeric.toHexStringWithPrefix(getNonce()) : "null",
                getData() != null ? BytesUtils.toHexString(getData()) : "null",
                getIsFakeMsg() ? "YES" : "NO");
    }
}
