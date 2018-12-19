package com.horizen.transaction;

import scala.util.Try;
import scorex.core.serialization.BytesSerializable;
import scorex.core.serialization.Serializer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

class ListSerializer<T extends BytesSerializable> implements Serializer<ArrayList<T>> {
    private HashMap<Integer, Serializer<T>> _serializers; // unique key + serializer

    ListSerializer(HashMap<Integer, Serializer<T>> serializers) {
        _serializers = serializers;
    }

    @Override
    public byte[] toBytes(ArrayList<T> obj) {
        ArrayList<Integer> lengthList = new ArrayList<Integer>();

        ByteArrayOutputStream res = new ByteArrayOutputStream();
        ByteArrayOutputStream entireRes = new ByteArrayOutputStream();
        for (T t : obj) {
            Integer idOfSerializer = 0;// get id from _serializers
            byte[] tBytes = t.bytes();
            lengthList.add(idOfSerializer.byteValue() + tBytes.length);

            try {
                entireRes.write(idOfSerializer);
                entireRes.write(t.bytes());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        try {
            res.write(lengthList.size());
            for (Integer i : lengthList) {
                res.write(i);
            }
            res.write(entireRes.toByteArray());
        } catch (IOException e) {
            e.printStackTrace();
        }

        return res.toByteArray();
    }

    @Override
    public Try<ArrayList<T>> parseBytes(byte[] bytes) {
        // TO DO: implement backward logic
        return null;
    }
}
