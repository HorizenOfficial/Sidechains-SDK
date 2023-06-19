package io.horizen.json.serializer;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import io.horizen.utils.BytesUtils;

import java.io.IOException;

// Used for MC data types which are represented in BigEndian in MC RPC methods.
// Note: Internal representation of such types in MC is in LittleEndian.
public class ReverseBytesSerializer extends JsonSerializer<byte[]> {
    @Override
    public void serialize(byte[] bytes, JsonGenerator jsonGenerator, SerializerProvider serializerProvider) throws IOException {
        jsonGenerator.writeString(BytesUtils.toHexString(BytesUtils.toMainchainFormat(bytes)));
    }
}