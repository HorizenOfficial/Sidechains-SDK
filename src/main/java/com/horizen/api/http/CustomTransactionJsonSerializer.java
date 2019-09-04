package com.horizen.api.http;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import com.fasterxml.jackson.databind.util.StdConverter;

import java.io.IOException;

public class CustomTransactionJsonSerializer extends JsonSerializer<Long> {

    @Override
    public void serialize(Long aLong, JsonGenerator jsonGenerator, SerializerProvider serializerProvider) throws IOException {
        ScTimestamp msg = new ScTimestamp();
        msg.setTimestamp(aLong);
        //jsonGenerator.writeStartObject();
        //jsonGenerator.writeObjectField("myTimestamp", msg);
        //jsonGenerator.writeEndObject();
        jsonGenerator.writeObject(msg);
    }
}
