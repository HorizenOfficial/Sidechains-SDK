package com.horizen.vrf;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;

public class VrfProofJsonSerializer extends JsonSerializer<VrfProof> {
    @Override
    public void serialize(VrfProof value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        gen.writeBinary(value.bytes());
    }
}
