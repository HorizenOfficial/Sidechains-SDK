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

    /**
     * Zero address: 0x000...000
     */
    public static final Address ZERO = new Address(new byte[LENGTH]);

    private Address(byte[] bytes) {
        this.bytes = bytes;
    }

    public static Address fromBytes(byte[] bytes) {
        if (bytes == null) {
            return null;
        }
        if (bytes.length != LENGTH) {
            throw new IllegalArgumentException("address must have a length of " + LENGTH + " bytes");
        }
        // create a copy to make sure there is no outside reference to the address bytes
        return new Address(Arrays.copyOf(bytes, LENGTH));
    }

    public static Address fromHex(String hex) {
        if (!hex.startsWith("0x")) {
            throw new IllegalArgumentException("address must be prefixed with 0x");
        }
        return fromHexNoPrefix(hex.substring(2));
    }

    public static Address fromHexNoPrefix(String hex) {
        if (hex.length() != LENGTH * 2) {
            throw new IllegalArgumentException("address must have a length of " + LENGTH * 2 + " hex characters");
        }
        return new Address(Converter.fromHexString(hex));
    }

    @Override
    public String toString() {
        return "0x" + Converter.toHexString(bytes);
    }

    public String toStringNoPrefix() {
        return Converter.toHexString(bytes);
    }

    public byte[] toBytes() {
        return Arrays.copyOf(bytes, LENGTH);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Address address = (Address) o;
        return Arrays.equals(bytes, address.bytes);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(bytes);
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
            return Address.fromHex(jsonParser.getText());
        }
    }
}
