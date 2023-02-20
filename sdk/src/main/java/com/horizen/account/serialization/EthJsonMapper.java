package com.horizen.account.serialization;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.module.scala.DefaultScalaModule;
import com.horizen.evm.utils.BigIntegerDeserializer;
import com.horizen.evm.utils.BigIntegerSerializer;

import java.math.BigInteger;

public class EthJsonMapper {

    private static final ObjectMapper mapper;

    static {
        var module = new SimpleModule();
        module.addSerializer(BigInteger.class, new BigIntegerSerializer());
        module.addDeserializer(BigInteger.class, new BigIntegerDeserializer());
        module.addSerializer(byte[].class, new EthByteSerializer());
        module.addDeserializer(byte[].class, new EthByteDeserializer());
        mapper = new ObjectMapper();
        mapper.registerModule(new DefaultScalaModule());
        mapper.registerModule(module);
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
    }

    private EthJsonMapper() {
        // prevent instatiation
    }

    public static String serialize(Object value) throws Exception {
        return mapper.writeValueAsString(value);
    }

    public static <T> T deserialize(String json, Class<T> type) throws Exception {
        return mapper.readValue(json, type);
    }

    public static ObjectMapper getMapper() {
        // return copy to prevent outside modification of the mapper
        return mapper.copy();
    }
}
