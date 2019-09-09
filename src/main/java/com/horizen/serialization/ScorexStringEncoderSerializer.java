package com.horizen.serialization;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import scorex.core.utils.ScorexEncoder;

import java.io.IOException;

public class ScorexStringEncoderSerializer extends JsonSerializer<String> {

    @Override
    public void serialize(String t, JsonGenerator jsonGenerator, SerializerProvider serializerProvider) throws IOException {
        ScorexEncoder encoder = new ScorexEncoder();
        jsonGenerator.writeString(encoder.encode(t));
    }
}
