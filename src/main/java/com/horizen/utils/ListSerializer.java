package com.horizen.utils;

import com.google.common.primitives.Ints;
import javafx.util.Pair;
import scala.util.Failure;
import scala.util.Success;
import scala.util.Try;
import scorex.core.serialization.BytesSerializable;
import scorex.core.serialization.Serializer;

import java.io.ByteArrayOutputStream;
import java.util.*;

public class ListSerializer<T extends BytesSerializable> implements Serializer<List<T>> {
    private HashMap<Integer, Serializer<T>> _serializers; // unique key : serializer
    private HashMap<Class, Integer> _serializersClasses; // serializer class : unique key
    private int _maxListLength; // Used during parsing bytes. Not positive value for unlimited lists support.

    public ListSerializer(HashMap<Integer, Serializer<T>> serializers) {
        this(serializers, 0);
    }

    public ListSerializer(HashMap<Integer, Serializer<T>> serializers, int maxListLength)
     {
         _maxListLength = maxListLength;
        _serializers = serializers;
        _serializersClasses = new HashMap<>();
        for(Map.Entry<Integer, Serializer<T>> entry : _serializers.entrySet()){
            _serializersClasses.put(entry.getValue().getClass(), entry.getKey());
        }
        if(_serializers.size() != _serializersClasses.size())
            throw new IllegalArgumentException("Serializers class types expected to be unique.");
    }

    @Override
    public byte[] toBytes(List<T> obj) {
        // Serialized data has the next order:
        // Size of an array with List<T> objects lengths    4 bytes
        // Array with objects length                        4 bytes multiplied by value above
        // Array with objects                               rest of bytes

        // Each object in array has the next structure:
        // Serializer ID                                    4 bytes
        // Object itself                                    rest of bytes

        List<Integer> lengthList = new ArrayList<>();

        ByteArrayOutputStream res = new ByteArrayOutputStream();
        ByteArrayOutputStream entireRes = new ByteArrayOutputStream();
        for (T t : obj) {
            // get proper Serializer or return nothing
            int idOfSerializer = 0;
            boolean serializerFound = _serializersClasses.containsKey(t.serializer().getClass());

            if(serializerFound)
                idOfSerializer = _serializersClasses.get(t.serializer().getClass());
            else
                throw new IllegalArgumentException("Object without defined serializer occurred.");

            byte[] tBytes = t.bytes();
            lengthList.add(4 + tBytes.length); // size of Int + size of serialized T object
            entireRes.write(Ints.toByteArray(idOfSerializer), 0, 4);
            entireRes.write(tBytes, 0, tBytes.length);
        }

        res.write(Ints.toByteArray(lengthList.size()), 0, 4);
        for (Integer i : lengthList) {
            res.write(Ints.toByteArray(i), 0, 4);
        }
        byte[] entireResBytes = entireRes.toByteArray();
        res.write(entireResBytes, 0 , entireResBytes.length);

        return res.toByteArray();
    }

    @Override
    public Try<List<T>> parseBytes(byte[] bytes) {
        try {
            int offset = 0;

            if(bytes.length < 4)
                throw new IllegalArgumentException("Input data corrupted.");
            int lengthListSize = ParseBytesUtils.getInt(bytes, offset);
            offset += 4;

            if(_maxListLength > 0 && lengthListSize > _maxListLength)
                throw new IllegalArgumentException("Input data contains to many elements.");

            if(bytes.length < offset + lengthListSize * 4)
                throw new IllegalArgumentException("Input data corrupted.");
            int objectsTotalLength = 0;
            ArrayList<Integer> lengthList = new ArrayList<>();
            while(offset < 4 * lengthListSize + 4) {
                int objectLength = ParseBytesUtils.getInt(bytes, offset);
                objectsTotalLength += objectLength;
                lengthList.add(objectLength);
                offset += 4;
            }

            if(bytes.length != offset + objectsTotalLength)
                throw new IllegalArgumentException("Input data corrupted.");
            // Pair <serializer id : bytes>
            ArrayList<Pair<Integer, byte[]>> objects = new ArrayList<>();
            for(int length : lengthList) {
                int serializerId = ParseBytesUtils.getInt(bytes, offset);
                offset += 4;
                objects.add(new Pair<>(serializerId, Arrays.copyOfRange(bytes, offset, offset + length - 4)));
                offset += length - 4;
            }


            ArrayList<T> res = new ArrayList<>();
            for(Pair<Integer, byte[]> obj : objects) {
                if(!_serializers.containsKey(obj.getKey()))
                    throw new IllegalArgumentException("Input data corrupted.");
                Try<T> t =_serializers.get(obj.getKey()).parseBytes(obj.getValue());
                if(t.isFailure())
                    throw new IllegalArgumentException("Input data corrupted.");
                res.add(t.get());
            }

            return new Success<>(res);
        }
        catch(IllegalArgumentException e) {
            return new Failure<>(e);
        }
    }
}
