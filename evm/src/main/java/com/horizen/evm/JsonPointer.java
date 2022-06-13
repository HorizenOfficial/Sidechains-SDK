package com.horizen.evm;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.horizen.evm.utils.*;
import com.sun.jna.FromNativeContext;
import com.sun.jna.NativeMapped;
import com.sun.jna.Pointer;

import java.io.IOException;
import java.math.BigInteger;

public class JsonPointer implements NativeMapped {
    private static final ObjectMapper mapper;

    static {
        var module = new SimpleModule();
        module.addSerializer(BigInteger.class, new BigIntSerializer());
        module.addSerializer(Address.class, new Address.Serializer());
        module.addSerializer(Hash.class, new Hash.Serializer());
        module.addDeserializer(BigInteger.class, new BigIntDeserializer());
        module.addDeserializer(Address.class, new Address.Deserializer());
        module.addDeserializer(Hash.class, new Hash.Deserializer());
        mapper = new ObjectMapper();
        mapper.registerModule(module);
        // do not serialize null or empty values
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        mapper.setSerializationInclusion(JsonInclude.Include.NON_EMPTY);
    }

    private final String json;

    public JsonPointer() {
        this("");
    }

    public JsonPointer(String json) {
        this.json = json;
    }

    /**
     * When receiving data from native we expect it to be a pointer to a standard C string, i.e. null-terminated, that
     * is copied to an instance of JsonPointer and free'ed on the native end. Deserialization is deferred to
     * deserialize() because we need additional type information to do so.
     */
    @Override
    public Object fromNative(Object nativeValue, FromNativeContext context) {
        if (nativeValue == null) {
            return null;
        } else {
            var ptr = (Pointer) nativeValue;
            try {
                // copy json string from native memory
                return new JsonPointer(ptr.getString(0));
            } finally {
                // free the string pointer on the native end
                LibEvm.Free(ptr);
            }
        }
    }

    /**
     * When serializing to pass data to native we expect this class to be an instance of a subclass of JsonPointer.
     */
    @Override
    public Object toNative() {
        try {
            return mapper.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("JSON processing error " + this.getClass());
        }
    }

    @Override
    public Class<?> nativeType() {
        return Pointer.class;
    }

    public <T> T deserialize(JavaType type) {
        try {
            return mapper.readValue(json, type);
        } catch (IOException e) {
            throw new IllegalArgumentException(e);
        }
    }
}
