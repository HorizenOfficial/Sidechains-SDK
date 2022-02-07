package com.horizen.serialization;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.horizen.utils.BytesUtils;
import com.horizen.utils.MerklePath;

import java.io.IOException;

public class MerklePathJsonSerializer extends JsonSerializer<MerklePath> {
    @Override
    public void serialize(MerklePath value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        gen.writeString(BytesUtils.toHexString(value.bytes()));
    }
}
