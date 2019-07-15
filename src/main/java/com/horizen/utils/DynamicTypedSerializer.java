package com.horizen.utils;

import com.google.common.primitives.Bytes;
import scala.util.Failure;
import scala.util.Try;
import scorex.core.serialization.BytesSerializable;
import scorex.core.serialization.ScorexSerializer;
import scorex.util.serialization.Reader;
import scorex.util.serialization.Writer;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class DynamicTypedSerializer<T extends BytesSerializable, S extends ScorexSerializer<? extends T>> implements ScorexSerializer<T> {
    protected HashMap<Byte, S> _coreSerializers; // unique core key : core serializer
    protected HashMap<Class, Byte> _coreSerializersClasses; // core serializer class : unique core key

    protected HashMap<Byte, S> _customSerializers; // unique custom key : custom serializer
    protected HashMap<Class, Byte> _customSerializersClasses; // custom serializer class : unique custom key

    protected byte CUSTOM_SERIALIZER_TYPE = Byte.MAX_VALUE;


    public DynamicTypedSerializer(HashMap<Byte, S> coreSerializers, HashMap<Byte, S> customSerializers) {
        _coreSerializers = coreSerializers;
        _customSerializers = customSerializers;

        _coreSerializersClasses = new HashMap<>();
        for(Map.Entry<Byte, S> entry : _coreSerializers.entrySet()){
            _coreSerializersClasses.put(entry.getValue().getClass(), entry.getKey());
        }
        if(_coreSerializers.size() != _coreSerializersClasses.size())
            throw new IllegalArgumentException("Core Serializers class types expected to be unique.");

        _customSerializersClasses = new HashMap<>();
        for(Map.Entry<Byte, S> entry : _customSerializers.entrySet()){
            _customSerializersClasses.put(entry.getValue().getClass(), entry.getKey());
        }
        if(_customSerializers.size() != _customSerializersClasses.size())
            throw new IllegalArgumentException("Custom Serializers class types expected to be unique.");
    }

    @Override
    public byte[] toBytes(T obj) {
        // Core serializer found
        if(_coreSerializersClasses.containsKey(obj.serializer().getClass())) {
            byte idOfSerializer = _coreSerializersClasses.get(obj.serializer().getClass());
            return Bytes.concat(
                    new byte[] { idOfSerializer },
                    obj.serializer().toBytes(obj)
            );
        }
        else if(_customSerializersClasses.containsKey(obj.serializer().getClass())) {
            byte idOfSerializer = _customSerializersClasses.get(obj.serializer().getClass());
            return Bytes.concat(
                    new byte[] { CUSTOM_SERIALIZER_TYPE, idOfSerializer },
                    obj.serializer().toBytes(obj)
            );
        }
        else
            throw new IllegalArgumentException("Object without defined serializer occurred.");
    }

    @Override
    public void serialize(T obj, Writer writer) {

    }

    @Override
    public T parse(Reader reader) {
        return null;
    }

    @Override
    public Try<T> parseBytesTry(byte[] bytes) {
        try {
            if (bytes == null || bytes.length < 1)
                throw new IllegalArgumentException("Unknown custom type id.");
            if (bytes[0] == CUSTOM_SERIALIZER_TYPE) {
                // try parse using custom serializers
                S serializer = _customSerializers.get(bytes[1]);
                if (serializer != null)
                    return (Try<T>) serializer.parseBytes(Arrays.copyOfRange(bytes, 2, bytes.length));
                else
                    throw new IllegalArgumentException("Unknown custom type id.");
            } else {
                // try parse using core serializers
                S serializer = _coreSerializers.get(bytes[0]);
                if (serializer != null)
                    return (Try<T>) serializer.parseBytes(Arrays.copyOfRange(bytes, 1, bytes.length));
                else
                    throw new IllegalArgumentException("Unknown core type id.");
            }
        } catch (Exception e) {
            return new Failure<>(e);
        }
    }
}
