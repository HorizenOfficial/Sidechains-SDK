package io.horizen.json.serializer;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;
import java.math.BigInteger;


public class HexNoPrefixBigIntegerSerializer extends JsonSerializer<BigInteger> {
    @Override
    public void serialize(BigInteger val, JsonGenerator jsonGenerator, SerializerProvider serializerProvider) throws IOException {
        jsonGenerator.writeString(val.toString(16));
    }
}