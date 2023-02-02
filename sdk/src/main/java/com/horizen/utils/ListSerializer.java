package com.horizen.utils;

import sparkz.core.serialization.BytesSerializable;
import sparkz.core.serialization.SparkzSerializer;
import sparkz.util.serialization.Reader;
import sparkz.util.serialization.Writer;
import java.util.*;

public class ListSerializer<T extends BytesSerializable> implements SparkzSerializer<List<T>> {
    private SparkzSerializer<T> serializer;
    private int maxListLength; // Used during parsing bytes. Not positive value for unlimited lists support.

    public ListSerializer(SparkzSerializer<T> serializer) {
        this(serializer, 0);
    }

    public ListSerializer(SparkzSerializer<T> serializer, int maxListLength) {
        this.maxListLength = maxListLength;
        this.serializer = serializer;
    }

    @Override
    public void serialize(List<T> objectsList, Writer writer) {
        if(maxListLength > 0 && objectsList.size() > maxListLength)
            throw new IllegalArgumentException("Serializable data contains too many elements - " + objectsList.size());

        writer.putInt(objectsList.size());

        for(T object : objectsList) {
            serializer.serialize(object, writer);
        }
    }

    @Override
    public List<T> parse(Reader reader) {
        int objectsCount = reader.getInt();

        if(objectsCount < 0)
            throw new IllegalArgumentException("Input data contains illegal elements count - " + objectsCount);

        if(maxListLength > 0 && objectsCount > maxListLength)
            throw new IllegalArgumentException("Input data contains to many elements - " + objectsCount);

        List<T> objectsList = new ArrayList<>(objectsCount);
        for(int i = 0; i < objectsCount; i++) {
            T parseObject = serializer.parse(reader);
            objectsList.add(parseObject);
        }

        return objectsList;
    }
}
