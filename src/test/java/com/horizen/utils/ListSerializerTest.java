package com.horizen.utils;

import com.google.common.primitives.Ints;
import org.bouncycastle.util.Strings;
import org.junit.Before;
import org.junit.Test;
import scala.util.Failure;
import scala.util.Success;
import scala.util.Try;
import scorex.core.serialization.BytesSerializable;
import scorex.core.serialization.Serializer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static org.junit.Assert.*;

class ListSerializerTestObjectA implements BytesSerializable {

    public String _testData;

    public ListSerializerTestObjectA(String testData) {
        _testData = testData;
    }
    @Override
    public byte[] bytes() {
        return serializer().toBytes(this);
    }

    @Override
    public Serializer serializer() {
        return new ListSerializerTestObjectASerializer();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null)
            return false;
        if (!(obj instanceof ListSerializerTestObjectA))
            return false;
        if (obj == this)
            return true;
        return this._testData.equals(((ListSerializerTestObjectA) obj)._testData);
    }
}

class ListSerializerTestObjectASerializer implements Serializer<ListSerializerTestObjectA> {

    @Override
    public byte[] toBytes(ListSerializerTestObjectA obj) {
        return (obj._testData).getBytes();
    }

    @Override
    public Try<ListSerializerTestObjectA> parseBytes(byte[] bytes) {
        try {
            return new Success<>(new ListSerializerTestObjectA(Strings.fromByteArray(bytes)));
        } catch (Exception e) {
            return new Failure<>(e);
        }

    }
}

class ListSerializerTestObjectB implements BytesSerializable {

    public int _testData;

    public ListSerializerTestObjectB(int testData) {
        _testData = testData;
    }
    @Override
    public byte[] bytes() {
        return serializer().toBytes(this);
    }

    @Override
    public Serializer serializer() {
        return new ListSerializerTestObjectBSerializer();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null)
            return false;
        if (!(obj instanceof ListSerializerTestObjectB))
            return false;
        if (obj == this)
            return true;
        return this._testData == ((ListSerializerTestObjectB) obj)._testData;
    }
}

class ListSerializerTestObjectBSerializer implements Serializer<ListSerializerTestObjectB> {

    @Override
    public byte[] toBytes(ListSerializerTestObjectB obj) {
        return Ints.toByteArray(obj._testData);
    }

    @Override
    public Try<ListSerializerTestObjectB> parseBytes(byte[] bytes) {
        try {
            return new Success<>(new ListSerializerTestObjectB(Ints.fromByteArray(bytes)));
        } catch (Exception e) {
            return new Failure<>(e);
        }

    }
}

public class ListSerializerTest {

    @Test
    public void ListSerializerTest_SerializationTest() {
        HashMap<Integer, Serializer<BytesSerializable>> serializers = new HashMap<>();
        serializers.put(1, (Serializer)new ListSerializerTestObjectASerializer());
        serializers.put(2, (Serializer)new ListSerializerTestObjectBSerializer());
        ListSerializer<BytesSerializable> listSerializer = new ListSerializer<>(serializers);

        ArrayList<BytesSerializable> data = new ArrayList<>();
        data.add(new ListSerializerTestObjectA("test1"));
        data.add(new ListSerializerTestObjectA("test2"));
        data.add(new ListSerializerTestObjectB(1));
        data.add(new ListSerializerTestObjectA("test3"));
        data.add(new ListSerializerTestObjectB(2));
        data.add(new ListSerializerTestObjectB(3));

        byte[] bytes = listSerializer.toBytes((List)data);
        List<BytesSerializable> res = listSerializer.parseBytes(bytes).get();

        assertEquals("Deserialized list has different size than original", data.size(), res.size());
        for(int i = 0; i < data.size(); i++)
            assertEquals("Deserialized list is different to original", true, data.get(i).equals(res.get(i)));
    }
}