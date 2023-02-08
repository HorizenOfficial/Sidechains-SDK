package com.horizen.evm.utils;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import java.io.IOException;

@JsonSerialize(using = Address.Serializer.class)
@JsonDeserialize(using = Address.Deserializer.class)
public class Address extends FixedSizeByteArray {
    public static final int LENGTH = 20;

    /**
     * Zero address: 0x0000000000000000000000000000000000000000
     */
    public static final Address ZERO = new Address(new byte[LENGTH]);

    public Address(byte[] bytes) {
        super(LENGTH, bytes);
    }

    public Address(String hex) {
        super(LENGTH, hex);
    }

    public static class Serializer extends JsonSerializer<Address> {
        @Override
        public void serialize(
            Address address, JsonGenerator jsonGenerator, SerializerProvider serializerProvider
        ) throws IOException {
            jsonGenerator.writeString(address.toString());
        }
    }

    public static class Deserializer extends JsonDeserializer<Address> {
        @Override
        public Address deserialize(
            JsonParser jsonParser, DeserializationContext deserializationContext
        ) throws IOException {
            return new Address(jsonParser.getText());
        }
    }
}
