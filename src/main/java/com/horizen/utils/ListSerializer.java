package com.horizen.utils;

import com.google.common.primitives.Ints;
import scala.util.Failure;
import scala.util.Success;
import scala.util.Try;
import scorex.core.serialization.BytesSerializable;
import scorex.core.serialization.Serializer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;

public class ListSerializer<T extends BytesSerializable> implements Serializer<List<T>> {
    private HashMap<Integer, Serializer<T>> _serializers; // unique key : serializer

    public ListSerializer(HashMap<Integer, Serializer<T>> serializers) {
        _serializers = serializers;
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
            boolean serializerFound = false;
            Iterator it = _serializers.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<Integer, Serializer<T>> entry = (Map.Entry)it.next();
                if(t.serializer().getClass() == entry.getValue().getClass()) {
                    idOfSerializer = entry.getKey();
                    serializerFound = true;
                    break;
                }
            }
            if(!serializerFound)
                return new byte[0];

            byte[] tBytes = t.bytes();
            lengthList.add(4 + tBytes.length); // size of Int + size of serialized T object

            try {
                entireRes.write(Ints.toByteArray(idOfSerializer));
                entireRes.write(tBytes);
            } catch (IOException e) {
                return new byte[0];
            }
        }

        try {
            res.write(Ints.toByteArray(lengthList.size()));
            for (Integer i : lengthList) {
                res.write(Ints.toByteArray(i));
            }
            res.write(entireRes.toByteArray());
        } catch (IOException e) {
            return new byte[0];
        }

        return res.toByteArray();
    }

    @Override
    public Try<List<T>> parseBytes(byte[] bytes) {
        try {
            int offset = 0;

            int lengthListSize = Ints.fromByteArray(Arrays.copyOfRange(bytes, offset, offset + 4));
            offset += 4;

            ArrayList<Integer> lengthList = new ArrayList<>();
            while(offset < 4 * lengthListSize + 4) {
                lengthList.add(Ints.fromByteArray(Arrays.copyOfRange(bytes, offset, offset + 4)));
                offset += 4;
            }

            ArrayList<byte[]> objects = new ArrayList<>();
            for(int length : lengthList) {
                objects.add(Arrays.copyOfRange(bytes, offset, offset + length));
                offset += length;
            }

            if(bytes.length != offset)
                throw new IllegalArgumentException("Input data corrupted.");


            ArrayList<T> res = new ArrayList<>();
            for(byte[] obj : objects) {
                int idOfSerializer = Ints.fromByteArray(Arrays.copyOfRange(obj, 0, 4));
                if(!_serializers.containsKey(idOfSerializer))
                    throw new IllegalArgumentException("Input data corrupted.");
                Try<T> t =_serializers.get(idOfSerializer).parseBytes(Arrays.copyOfRange(obj, 4, obj.length));
                if(t.isFailure())
                    throw new IllegalArgumentException("Input data corrupted.");
                res.add(t.get());
            }

            return new Success<>(res);
        } catch (Exception e) {
            return new Failure<>(new IllegalArgumentException("Input data corrupted."));
        }
    }
}
