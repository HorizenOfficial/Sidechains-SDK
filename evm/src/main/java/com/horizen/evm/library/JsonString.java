package com.horizen.evm.library;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.jna.FromNativeContext;
import com.sun.jna.NativeMapped;
import com.sun.jna.Pointer;

import java.io.IOException;

public class JsonString implements NativeMapped {
    private final String json;

    public JsonString() {
        this("");
    }

    public JsonString(String json) {
        this.json = json;
    }

    @Override
    public Object fromNative(Object nativeValue, FromNativeContext context) {
        if (nativeValue == null) {
            return null;
        } else {
            var ptr = (Pointer) nativeValue;
            try {
                // copy json string from native memory
                return new JsonString(ptr.getString(0));
            } finally {
                // free the string pointer on the native end
                LibEvm.Instance.Free(ptr);
            }
        }
    }

    @Override
    public Object toNative() {
        return json;
    }

    @Override
    public Class<?> nativeType() {
        return Pointer.class;
    }

    public <T> T deserialize(JavaType type) {
        try {
            // parse json into object
            var objectMapper = new ObjectMapper();
            return objectMapper.readValue(json, type);
//        } catch (JsonMappingException e) {
//            throw new IllegalArgumentException("JSON mapping error " + this.getClass());
//        } catch (JsonParseException e) {
//            throw new IllegalArgumentException("JSON parse error " + this.getClass());
        } catch (IOException e) {
            throw new IllegalArgumentException(e);
        }
    }
}
