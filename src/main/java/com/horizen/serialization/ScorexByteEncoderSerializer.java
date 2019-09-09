package com.horizen.serialization;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import scorex.core.utils.ScorexEncoder;

import java.io.IOException;

public class ScorexByteEncoderSerializer extends JsonSerializer<byte[]> {

    @Override
    public void serialize(byte[] bytes, JsonGenerator jsonGenerator, SerializerProvider serializerProvider) throws IOException {
        ScorexEncoder encoder = new ScorexEncoder();
        jsonGenerator.writeString(encoder.encode(bytes));
    }
}
