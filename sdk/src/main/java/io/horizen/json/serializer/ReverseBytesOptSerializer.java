package io.horizen.json.serializer;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import io.horizen.utils.BytesUtils;
import scala.Option;

import java.io.IOException;

public class ReverseBytesOptSerializer extends JsonSerializer<Option<byte[]>> {
    @Override
    public void serialize(Option<byte[]> bytesOpt, JsonGenerator jsonGenerator, SerializerProvider serializerProvider) throws IOException {
        if(bytesOpt.isDefined())
            jsonGenerator.writeString(BytesUtils.toHexString(BytesUtils.reverseBytes(bytesOpt.get())));
    }
}
