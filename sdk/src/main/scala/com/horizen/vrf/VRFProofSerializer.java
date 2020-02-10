package com.horizen.vrf;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;

public class VRFProofSerializer extends JsonSerializer<VRFProof> {
    @Override
    public void serialize(VRFProof value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        gen.writeBinary(value.bytes());
    }
}
