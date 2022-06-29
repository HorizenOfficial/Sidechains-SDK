package com.horizen.account.state;

import com.horizen.account.proposition.AddressProposition;
import com.horizen.utils.BytesUtils;
import junit.framework.TestCase;

import java.math.BigInteger;

public abstract class MessageProcessorTestBase extends TestCase {
    protected static final byte[] hashNull =
            BytesUtils.fromHexString("0000000000000000000000000000000000000000000000000000000000000000");

    protected static final byte[] originAddress = BytesUtils.fromHexString("00000000000000000000000000000000FFFFFF01");
    protected static final byte[] emptyAddress = BytesUtils.fromHexString("00000000000000000000000000000000FFFFFF02");
    protected static final byte[] eoaAddress = BytesUtils.fromHexString("00000000000000000000000000000000FFFFFF03");
    protected static final byte[] contractAddress =
            BytesUtils.fromHexString("00000000000000000000000000000000FFFFFF04");

    protected Message getMessage(byte[] destination, byte[] data) {
        var gas = BigInteger.valueOf(200000);
        var price = BigInteger.ZERO;
        var value = BigInteger.ZERO;
        var nonce = BigInteger.valueOf(234);
        AddressProposition to = destination == null ? null : new AddressProposition(destination);
        var from = new AddressProposition(originAddress);
        return new Message(from, to, price, price, price, gas, value, nonce, data);
    }

    protected Message getMessage(byte[] destination) {
        return getMessage(destination, null);
    }
}
