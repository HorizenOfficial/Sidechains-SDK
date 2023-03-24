package io.horizen.json.serializer;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import io.horizen.utils.BytesUtils;
import io.horizen.utils.MerklePath;

import java.io.IOException;

public class MerklePathJsonSerializer extends JsonSerializer<MerklePath> {
    @Override
    public void serialize(MerklePath value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        gen.writeString(BytesUtils.toHexString(value.bytes()));
    }
}
