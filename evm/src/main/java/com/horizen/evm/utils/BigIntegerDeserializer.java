package com.horizen.evm.utils;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import java.io.IOException;
import java.math.BigInteger;

public class BigIntegerDeserializer extends JsonDeserializer<BigInteger> {
    @Override
    public BigInteger deserialize(JsonParser jsonParser, DeserializationContext ctx) throws IOException {
        var text = jsonParser.getText();
        if (text == null || !text.startsWith("0x")) {
            throw new IOException("quantity must start with 0x");
        }
        return new BigInteger(text.substring(2), 16);
    }
}
