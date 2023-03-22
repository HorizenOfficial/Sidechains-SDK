package io.horizen.account.serialization;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import io.horizen.utils.BytesUtils;

import java.io.IOException;

public class EthByteDeserializer extends JsonDeserializer<byte[]> {
    @Override
    public byte[] deserialize(JsonParser jsonParser, DeserializationContext ctx) throws IOException {
        String text = jsonParser.getText();
        if (text != null && text.startsWith("0x")) {
            return BytesUtils.fromHexString(text.substring(2));
        } else {
            throw new IOException("data must start with \"0x\" but received: " + text);
        }
    }
}
