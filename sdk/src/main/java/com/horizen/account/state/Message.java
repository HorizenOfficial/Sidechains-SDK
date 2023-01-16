package com.horizen.account.state;

import com.horizen.account.proposition.AddressProposition;

import java.math.BigInteger;
import java.util.Arrays;

public class Message {
    private final AddressProposition from;
    private final AddressProposition to;

    private final BigInteger gasPrice;
    private final BigInteger gasFeeCap;
    private final BigInteger gasTipCap;
    private final BigInteger gasLimit;

    private final BigInteger value;
    private final BigInteger nonce;
    private final byte[] data;

    private final boolean fakeMsg;

    public Message(
            AddressProposition from,
            AddressProposition to,
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

    public AddressProposition getFrom() {
        return from;
    }

    public AddressProposition getTo() {
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
}
