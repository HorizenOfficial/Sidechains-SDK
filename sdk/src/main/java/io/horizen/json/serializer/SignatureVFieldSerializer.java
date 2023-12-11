package io.horizen.json.serializer;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import io.horizen.account.transaction.EthereumTransaction;
import org.web3j.crypto.Sign;

import java.io.IOException;
import java.math.BigInteger;


public class SignatureVFieldSerializer extends JsonSerializer<BigInteger> {
    @Override
    public void serialize(BigInteger val, JsonGenerator jsonGenerator, SerializerProvider serializerProvider) throws IOException {
        try {
            Object pojo = jsonGenerator.getOutputContext().getParent().getCurrentValue();
            if (pojo instanceof EthereumTransaction && ((EthereumTransaction) pojo).isLegacy())
                jsonGenerator.writeString(val.toString(16));
            else
                jsonGenerator.writeString(val.subtract(BigInteger.valueOf(Sign.LOWER_REAL_V)).toString(16));
        } catch (Exception e) {
            // default serialization in case Signature is not a part of EthereumTransaction
            jsonGenerator.writeString(val.toString(16));
        }
    }
}