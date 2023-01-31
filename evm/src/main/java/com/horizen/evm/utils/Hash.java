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

@JsonSerialize(using = Hash.Serializer.class)
@JsonDeserialize(using = Hash.Deserializer.class)
public class Hash extends FixedSizeByteArray {
    public static final int LENGTH = 32;

    /**
     * Zero hash: 0x0000000000000000000000000000000000000000000000000000000000000000
     */
    public static final Hash ZERO = new Hash(new byte[LENGTH]);

    public Hash(byte[] bytes) {
        super(LENGTH, bytes);
    }

    public Hash(String hex) {
        super(LENGTH, hex);
    }

    public static Hash fromBytes(byte[] bytes) {
        if (bytes == null) {
            return null;
        }
        return new Hash(bytes);
    }

    public static class Serializer extends JsonSerializer<Hash> {
        @Override
        public void serialize(
            Hash hash, JsonGenerator jsonGenerator, SerializerProvider serializerProvider
        ) throws IOException {
            jsonGenerator.writeString(hash.toString());
        }
    }

    public static class Deserializer extends JsonDeserializer<Hash> {
        @Override
        public Hash deserialize(
            JsonParser jsonParser, DeserializationContext deserializationContext
        ) throws IOException {
            var text = jsonParser.getText();
            if (!text.startsWith("0x")) {
                throw new IOException("hash must be prefixed with 0x");
            }
            return Hash.fromBytes(Converter.fromHexString(text.substring(2)));
        }
    }
}
