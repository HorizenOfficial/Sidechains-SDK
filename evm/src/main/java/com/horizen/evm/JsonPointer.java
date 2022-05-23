package com.horizen.evm;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.jna.FromNativeContext;
import com.sun.jna.NativeMapped;
import com.sun.jna.Pointer;

import java.io.IOException;

public class JsonPointer implements NativeMapped {
    @Override
    public Object fromNative(Object nativeValue, FromNativeContext fromNativeContext) {
        if (nativeValue == null) {
            return null;
        } else {
            Pointer ptr = (Pointer) nativeValue;
            try {
                // copy json string from native memory
                String json = ptr.getString(0);
                // parse json into object
                ObjectMapper objectMapper = new ObjectMapper();
                return objectMapper.readValue(json, this.getClass());
//            } catch (JsonMappingException e) {
//                throw new IllegalArgumentException("JSON mapping error " + this.getClass());
//            } catch (JsonParseException e) {
//                throw new IllegalArgumentException("JSON parse error " + this.getClass());
            } catch (IOException e) {
                throw new IllegalArgumentException(e);
            } finally {
                // free the string pointer on the native end
                Evm.Instance.Free(ptr);
            }
        }
    }

    @Override
    public Object toNative() {
        try {
            // serialize object to json
            ObjectMapper mapper = new ObjectMapper();
            // do not serialize null or empty values
            mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
            mapper.setSerializationInclusion(JsonInclude.Include.NON_EMPTY);
            return mapper.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("JSON processing error " + this.getClass());
        }
    }

    @Override
    public Class<?> nativeType() {
        return Pointer.class;
    }
}
