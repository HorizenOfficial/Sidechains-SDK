package com.horizen.evm.utils;

import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;
import java.util.Arrays;

public class Hash {
    public static final int LENGTH = 32;
    private final byte[] bytes;

    private Hash(byte[] bytes) {
        if (bytes.length != LENGTH) {
            throw new IllegalArgumentException("hash must have a length of " + LENGTH);
        }
        this.bytes = bytes;
    }

    public static Hash FromBytes(byte[] bytes) {
        if (bytes == null) {
            return null;
        }
        return new Hash(bytes);
    }

    public byte[] toBytes() {
        return Arrays.copyOf(bytes, LENGTH);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Hash h = (Hash) o;
        return Arrays.equals(this.bytes, h.bytes);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(bytes);
    }

    @JsonValue
    @Override
    public String toString() {
        return "0x" + Converter.toHexString(bytes);
    }

    public static class Serializer extends JsonSerializer<Hash> {
        @Override
        public void serialize(Hash hash, JsonGenerator jsonGenerator, SerializerProvider serializerProvider)
            throws IOException {
            jsonGenerator.writeString(hash.toString());
        }
    }

    public static class Deserializer extends JsonDeserializer<Hash> {
        @Override
        public Hash deserialize(JsonParser jsonParser, DeserializationContext deserializationContext)
            throws IOException {
            var text = jsonParser.getText();
            if (!text.startsWith("0x")) {
                throw new IOException("hash must be prefixed with 0x");
            }
            return Hash.FromBytes(Converter.fromHexString(text.substring(2)));
        }
    }
}
