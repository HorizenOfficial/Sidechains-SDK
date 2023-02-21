package com.horizen.evm;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
import com.google.common.io.BaseEncoding;
import com.horizen.evm.utils.BigIntegerDeserializer;
import com.horizen.evm.utils.BigIntegerSerializer;

import java.io.IOException;
import java.math.BigInteger;

final class Converter {
    private static final ObjectMapper mapper;

    static {
        var module = new SimpleModule();
        module.addSerializer(BigInteger.class, new BigIntegerSerializer());
        module.addDeserializer(BigInteger.class, new BigIntegerDeserializer());
        mapper = new ObjectMapper();
        mapper.registerModule(module);
        mapper.registerModule(new ParameterNamesModule());
        // do not serialize null or empty values
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        mapper.setSerializationInclusion(JsonInclude.Include.NON_EMPTY);
    }

    private Converter() { }

    /**
     * Serialize given object to JSON.
     *
     * @param obj target object to serialize
     * @return JSON string
     */
    public static String toJson(Object obj) {
        try {
            return mapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException(e);
        }
    }

    /**
     * Deserialize JSON content into the given type.
     *
     * @param type target type to deserialize to
     * @param <T>  expected return type
     * @return object instance deserialized from JSON
     */
    public static <T> T fromJson(String json, JavaType type) {
        try {
            return mapper.readValue(json, type);
        } catch (IOException e) {
            throw new IllegalArgumentException(e);
        }
    }

    // Get byte array from hex string
    public static byte[] fromHexString(String hex) {
        return BaseEncoding.base16().lowerCase().decode(hex.toLowerCase());
    }

    // Get hex string representation of byte array
    public static String toHexString(byte[] bytes) {
        return BaseEncoding.base16().lowerCase().encode(bytes);
    }
}
