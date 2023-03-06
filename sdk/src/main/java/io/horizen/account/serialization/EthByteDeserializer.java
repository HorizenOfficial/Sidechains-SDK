package io.horizen.account.serialization;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import org.web3j.utils.Numeric;

import java.io.IOException;

public class EthByteDeserializer extends JsonDeserializer<byte[]> {
    @Override
    public byte[] deserialize(JsonParser jsonParser, DeserializationContext ctx) throws IOException {
        return Numeric.hexStringToByteArray(jsonParser.getText());
    }
}
