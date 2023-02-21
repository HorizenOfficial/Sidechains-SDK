package com.horizen.evm;

import com.fasterxml.jackson.databind.JavaType;
import com.sun.jna.FromNativeContext;
import com.sun.jna.NativeMapped;
import com.sun.jna.Pointer;

public class JsonPointer implements NativeMapped {
    private final String json;

    public JsonPointer() {
        this("");
    }

    public JsonPointer(String json) {
        this.json = json;
    }

    /**
     * When receiving data from native we expect it to be a pointer to a standard C string, i.e. null-terminated
     * character array, that is copied to an instance of JsonPointer and free'ed on the native end. Deserialization is
     * deferred to deserialize() because we need additional type information to do so.
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
     * When serializing to pass data to native we expect this to be an instance of a subclass of JsonPointer.
     */
    @Override
    public Object toNative() {
        return Converter.toJson(this);
    }

    @Override
    public Class<?> nativeType() {
        return Pointer.class;
    }

    /**
     * Deserialize json content into the given type.
     *
     * @param type target type to deserialize to
     * @param <T>  expected return type
     * @return object instance deserialized from json
     */
    public <T> T deserialize(JavaType type) {
        return Converter.fromJson(json, type);
    }
}
