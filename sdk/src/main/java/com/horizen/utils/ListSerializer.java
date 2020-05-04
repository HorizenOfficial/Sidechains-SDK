package com.horizen.utils;

import scorex.core.serialization.BytesSerializable;
import scorex.core.serialization.ScorexSerializer;
import scorex.util.serialization.Reader;
import scorex.util.serialization.Writer;

import java.io.ByteArrayOutputStream;
import java.util.*;

public class ListSerializer<T extends BytesSerializable> implements ScorexSerializer<List<T>> {
    private ScorexSerializer<T> serializer;
    private int maxListLength; // Used during parsing bytes. Not positive value for unlimited lists support.

    public ListSerializer(ScorexSerializer<T> serializer) {
        this(serializer, 0);
    }

    public ListSerializer(ScorexSerializer<T> serializer, int maxListLength) {
        this.maxListLength = maxListLength;
        this.serializer = serializer;
    }

    @Override
    public void serialize(List<T> objectsList, Writer writer) {
        List<Integer> lengthsList = new ArrayList<>();
        ByteArrayOutputStream objects = new ByteArrayOutputStream();

        for(T object : objectsList) {
            byte[] objectBytes = serializer.toBytes(object);
            lengthsList.add(objectBytes.length);
            objects.write(objectBytes, 0, objectBytes.length);
        }

        writer.putInt(objectsList.size());

        for(int length : lengthsList)
            writer.putInt(length);

        writer.putBytes(objects.toByteArray());

    }

    @Override
    public List<T> parse(Reader reader) {
        int objectsCount = reader.getInt();
        int objectsTotalLength = 0;

        if(objectsCount < 0)
            throw new IllegalArgumentException("Input data contains illegal elements count - " + objectsCount);

        if(maxListLength > 0 && objectsCount > maxListLength)
            throw new IllegalArgumentException("Input data contains to many elements - " + objectsCount);

        List<Integer> lengthsList = new ArrayList<>();
        for(int i = 0; i < objectsCount; i++) {
            int length = reader.getInt();
            lengthsList.add(length);
            objectsTotalLength += length;
        }

        if (reader.remaining() < objectsTotalLength)
            throw new IllegalArgumentException("Input data is corrupted.");

        List<T> objectsList = new ArrayList<>(objectsCount);
        lengthsList.forEach(length -> objectsList.add(serializer.parseBytes(reader.getBytes(length))));

        return objectsList;
    }
}
