package com.horizen.json.serializer;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.horizen.utils.ByteArrayWrapper;
import com.horizen.utils.BytesUtils;

import java.io.IOException;

public class ByteArrayWrapperSerializer extends JsonSerializer<ByteArrayWrapper> {

    @Override
    public void serialize(ByteArrayWrapper baw, JsonGenerator jsonGenerator, SerializerProvider serializerProvider) throws IOException {
        jsonGenerator.writeString(BytesUtils.toHexString(baw.data()));
    }
}
