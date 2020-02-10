package com.horizen.utils;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;

public class MerklePathSerializer extends JsonSerializer<MerklePath> {
    @Override
    public void serialize(MerklePath value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        gen.writeBinary(value.bytes());
    }
}
