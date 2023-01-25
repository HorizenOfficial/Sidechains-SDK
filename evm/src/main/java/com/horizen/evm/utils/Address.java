package com.horizen.evm.utils;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.web3j.abi.datatypes.Type;

import java.io.IOException;
import java.util.Arrays;

@JsonSerialize(using = Address.Serializer.class)
@JsonDeserialize(using = Address.Deserializer.class)
public class Address implements Type<String> {
    public static final String TYPE_NAME = "address";
    public static final int LENGTH = 20;
    private final byte[] bytes;

    /**
     * Zero address: 0x000...000
     */
    public static final Address ZERO = new Address(new byte[LENGTH]);

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

    public static Address fromHex(String hex) {
        if (!hex.startsWith("0x")) {
            throw new IllegalArgumentException("address must be prefixed with 0x");
        }
        return new Address(Converter.fromHexString(hex.substring(2)));
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

    @Override
    public String getValue() {
        return toString();
    }

    @Override
    public String getTypeAsString() {
        return TYPE_NAME;
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
            return Address.fromHex(jsonParser.getText());
        }
    }
}
