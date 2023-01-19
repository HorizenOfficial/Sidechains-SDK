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
import java.util.Arrays;

@JsonSerialize(using = Address.Serializer.class)
@JsonDeserialize(using = Address.Deserializer.class)
public class Address {
    public static final int LENGTH = 20;
    private final byte[] bytes;

    private Address(byte[] bytes) {
        if (bytes.length != LENGTH) {
            throw new IllegalArgumentException("address must have a length of " + LENGTH);
        }
        this.bytes = bytes;
    }

    @Override
    public String toString() {
        return "0x" + Converter.toHexString(bytes);
    }

    public static Address fromBytes(byte[] bytes) {
        if (bytes == null) {
            return null;
        }
        return new Address(bytes);
    }

    public static Address addressZero() {
        return new Address(new byte[LENGTH]);
    }

    public byte[] toBytes() {
        return Arrays.copyOf(bytes, LENGTH);
    }

    public static class Serializer extends JsonSerializer<Address> {
        @Override
        public void serialize(
            Address address, JsonGenerator jsonGenerator, SerializerProvider serializerProvider
        ) throws IOException {
            jsonGenerator.writeString("0x" + Converter.toHexString(address.bytes));
        }
    }

    public static class Deserializer extends JsonDeserializer<Address> {
        @Override
        public Address deserialize(
            JsonParser jsonParser, DeserializationContext deserializationContext
        ) throws IOException {
            var text = jsonParser.getText();
            if (!text.startsWith("0x")) {
                throw new IOException("address must be prefixed with 0x");
            }
            return Address.fromBytes(Converter.fromHexString(text.substring(2)));
        }
    }
}
