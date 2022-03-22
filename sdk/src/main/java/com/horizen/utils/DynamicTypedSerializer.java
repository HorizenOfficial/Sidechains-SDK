package com.horizen.utils;

import scorex.core.serialization.BytesSerializable;
import scorex.core.serialization.ScorexSerializer;
import scorex.util.serialization.Reader;
import scorex.util.serialization.Writer;

import java.util.HashMap;
import java.util.Map;

public class DynamicTypedSerializer<T extends BytesSerializable, S extends ScorexSerializer<T>> implements ScorexSerializer<T> {
    protected HashMap<Byte, S> coreSerializers; // unique core key : core serializer
    protected HashMap<Class, Byte> coreSerializersClasses; // core serializer class : unique core key

    protected HashMap<Byte, S> customSerializers; // unique custom key : custom serializer
    protected HashMap<Class, Byte> customSerializersClasses; // custom serializer class : unique custom key

    protected byte CUSTOM_SERIALIZER_TYPE = Byte.MAX_VALUE;


    public DynamicTypedSerializer(HashMap<Byte, S> coreSerializers, HashMap<Byte, S> customSerializers) {
        this.coreSerializers = coreSerializers;
        this.customSerializers = customSerializers;

        coreSerializersClasses = new HashMap<>();
        for(Map.Entry<Byte, S> entry : this.coreSerializers.entrySet()){
            coreSerializersClasses.put(entry.getValue().getClass(), entry.getKey());
        }
        if(this.coreSerializers.size() != coreSerializersClasses.size())
            throw new IllegalArgumentException("Core Serializers class types expected to be unique.");

        customSerializersClasses = new HashMap<>();
        for(Map.Entry<Byte, S> entry : this.customSerializers.entrySet()){
            customSerializersClasses.put(entry.getValue().getClass(), entry.getKey());
        }
        if(this.customSerializers.size() != customSerializersClasses.size())
            throw new IllegalArgumentException("Custom Serializers class types expected to be unique.");
    }

    @Override
    public void serialize(T obj, Writer writer) {
        ScorexSerializer serializer = obj.serializer();
        // Core serializer found
        if(coreSerializersClasses.containsKey(serializer.getClass())) {
            byte idOfSerializer = coreSerializersClasses.get(serializer.getClass());
            writer.put(idOfSerializer);
            serializer.serialize(obj, writer);
        }
        else if(customSerializersClasses.containsKey(serializer.getClass())) {
            byte idOfSerializer = customSerializersClasses.get(serializer.getClass());
            writer.put(CUSTOM_SERIALIZER_TYPE);
            writer.put(idOfSerializer);
            serializer.serialize(obj, writer);
        }
        else
            throw new IllegalArgumentException("Object without defined serializer occurred.");
    }

    @Override
    public T parse(Reader reader) {
        if (reader.remaining() < 1)
            throw new IllegalArgumentException("Unknown custom type id.");

        byte type = reader.getByte();

        if (type == CUSTOM_SERIALIZER_TYPE) {
            // try parse using custom serializers
            byte customType = reader.getByte();

            S serializer = customSerializers.get(customType);
            if (serializer != null)
                return serializer.parse(reader);
            else
                throw new IllegalArgumentException("Unknown custom type id.");
        } else {
            // try parse using core serializers
            S serializer = coreSerializers.get(type);
            if (serializer != null)
                return serializer.parse(reader);
            else
                throw new IllegalArgumentException("Unknown core type id.");
        }
    }
}
