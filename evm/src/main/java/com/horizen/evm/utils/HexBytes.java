package com.horizen.evm.utils;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;
import java.util.Arrays;

public class HexBytes {
    private final byte[] bytes;

    private HexBytes(byte[] bytes) {
        this.bytes = bytes;
    }

    public static HexBytes FromBytes(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        return new HexBytes(bytes);
    }

    public byte[] toBytes() {
        return Arrays.copyOf(bytes, bytes.length);
    }

    public static class Serializer extends JsonSerializer<HexBytes> {
        @Override
        public void serialize(
            HexBytes address, JsonGenerator jsonGenerator, SerializerProvider serializerProvider
        ) throws IOException {
            jsonGenerator.writeString("0x" + Converter.toHexString(address.bytes));
        }
    }

    public static class Deserializer extends JsonDeserializer<HexBytes> {
        @Override
        public HexBytes deserialize(JsonParser jsonParser, DeserializationContext deserializationContext)
            throws IOException {
            var text = jsonParser.getText();
            if (!text.startsWith("0x")) {
                throw new IOException("address must be prefixed with 0x");
            }
            return HexBytes.FromBytes(Converter.fromHexString(text.substring(2)));
        }
    }
}
