package com.horizen.utils;

import org.bouncycastle.util.Strings;
import org.junit.Test;
import scorex.core.serialization.BytesSerializable;
import scorex.core.serialization.ScorexSerializer;
import scorex.util.serialization.Reader;
import scorex.util.serialization.Writer;

import java.util.ArrayList;
import java.util.Arrays;
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
    public ScorexSerializer serializer() {
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

    @Override
    public String toString() {
        return String.format("ListSerializerTestObjectA \"%s\"", _testData);
    }
}

class ListSerializerTestObjectASerializer implements ScorexSerializer<ListSerializerTestObjectA> {

    @Override
    public void serialize(ListSerializerTestObjectA obj, Writer writer) {
        writer.putInt(obj._testData.length());
        writer.putBytes(obj._testData.getBytes());
    }

    @Override
    public ListSerializerTestObjectA parse(Reader reader) {
        int length = reader.getInt();
        return new ListSerializerTestObjectA(Strings.fromByteArray(reader.getBytes(length)));
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
    public ScorexSerializer serializer() {
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

    @Override
    public String toString() {
        return String.format("ListSerializerTestObjectB \"%d\"", _testData);
    }
}

class ListSerializerTestObjectBSerializer implements ScorexSerializer<ListSerializerTestObjectB> {

    /*
    @Override
    public byte[] toBytes(ListSerializerTestObjectB obj) {
        return Ints.toByteArray(obj._testData);
    }

    @Override
    public Try<ListSerializerTestObjectB> parseBytesTry(byte[] bytes) {
        try {
            return new Success<>(new ListSerializerTestObjectB(Ints.fromByteArray(bytes)));
        } catch (Exception e) {
            return new Failure<>(e);
        }
    }
    */

    @Override
    public void serialize(ListSerializerTestObjectB obj, Writer writer) {
        writer.putInt(obj._testData);
    }

    @Override
    public ListSerializerTestObjectB parse(Reader reader) {
        return new ListSerializerTestObjectB(reader.getInt());
    }
}

public class ListSerializerTest {

    @Test
    public void ListSerializerTest_CreationTestForSingleType() {

        // Test 1: try to create ListSerializer with valid parameters and no limits
        boolean exceptionOccurred = false;
        try {
            new ListSerializer<>(new ListSerializerTestObjectASerializer());
        }
        catch (Exception e) {
            exceptionOccurred = true;
        }
        assertFalse("Unexpected exception occurred during creation without limits", exceptionOccurred);


        // Test 2: try to create ListSerializer with valid parameters and with limits
        try {
            new ListSerializer<>(new ListSerializerTestObjectASerializer(), 10);
        }
        catch (Exception e) {
            exceptionOccurred = true;
        }
        assertFalse("Unexpected exception occurred during creation with limits", exceptionOccurred);
    }

    @Test
    public void ListSerializerTest_CreationTestForMultipleTypes() {

        // Test 1: try to create ListSerializer with valid parameters and no limits
        boolean exceptionOccurred = false;
        try {
            DynamicTypedSerializer serializersCompanion = new DynamicTypedSerializer<>(
                    new HashMap<Byte, ScorexSerializer<BytesSerializable>>() {{
                        put((byte)1, (ScorexSerializer)new ListSerializerTestObjectASerializer());
                        put((byte)2, (ScorexSerializer)new ListSerializerTestObjectBSerializer());
                        }}, new HashMap<>()
            );
            new ListSerializer<>(serializersCompanion);
        }
        catch (Exception e) {
            exceptionOccurred = true;
        }
        assertFalse("Unexpected exception occurred during creation without limits", exceptionOccurred);


        // Test 2: try to create ListSerializer with valid parameters and with limits
        try {
            DynamicTypedSerializer serializersCompanion = new DynamicTypedSerializer<>(
                    new HashMap<Byte, ScorexSerializer<BytesSerializable>>() {{
                        put((byte)1, (ScorexSerializer)new ListSerializerTestObjectASerializer());
                        put((byte)2, (ScorexSerializer)new ListSerializerTestObjectBSerializer());
                    }}, new HashMap<>()
            );
            new ListSerializer<>(serializersCompanion, 10);
        }
        catch (Exception e) {
            exceptionOccurred = true;
        }
        assertFalse("Unexpected exception occurred during creation with limits", exceptionOccurred);


        // Test 3: try to create ListSerializer with invalid parameters (serializers duplications)
        try {
            DynamicTypedSerializer serializersCompanion = new DynamicTypedSerializer<>(
                    new HashMap<Byte, ScorexSerializer<BytesSerializable>>() {{
                        put((byte)1, (ScorexSerializer)new ListSerializerTestObjectASerializer());
                        put((byte)2, (ScorexSerializer)new ListSerializerTestObjectASerializer());
                    }}, new HashMap<>()
            );
            new ListSerializer<>(serializersCompanion, 10);
        }
        catch (Exception e) {
            exceptionOccurred = true;
        }
        assertTrue("Exception expected during creation", exceptionOccurred);
    }

    @Test
    public void ListSerializerTest_SerializationTestForSingleType() {

        ListSerializer<BytesSerializable> listSerializer = new ListSerializer<>((ScorexSerializer)new ListSerializerTestObjectASerializer());

        // Test 1: empty list serialization test
        byte[] bytes = listSerializer.toBytes(new ArrayList<>());
        List<BytesSerializable> res = listSerializer.parseBytesTry(bytes).get();
        assertEquals("Deserialized list should by empty", 0, res.size());

        // Test 2: not empty list with valid types
        ArrayList<BytesSerializable> data = new ArrayList<>();
        data.add(new ListSerializerTestObjectA("test1"));
        data.add(new ListSerializerTestObjectA("test2"));
        data.add(new ListSerializerTestObjectA("test3"));

        bytes = listSerializer.toBytes(data);
        res = listSerializer.parseBytesTry(bytes).get();

        assertEquals("Deserialized list has different size than original", data.size(), res.size());
        for(int i = 0; i < data.size(); i++)
            assertEquals(String.format("Deserialized list item %d is different to original", i), data.get(i), res.get(i));
    }

    @Test
    public void ListSerializerTest_SerializationTestForMultipleTypes() {
        DynamicTypedSerializer serializersCompanion = new DynamicTypedSerializer<>(
                new HashMap<Byte, ScorexSerializer<BytesSerializable>>() {{
                    put((byte)1, (ScorexSerializer)new ListSerializerTestObjectASerializer());
                    put((byte)2, (ScorexSerializer)new ListSerializerTestObjectBSerializer());
                }}, new HashMap<>()
        );
        ListSerializer<BytesSerializable> listSerializer = new ListSerializer<>(serializersCompanion);

        // Test 1: empty list serialization test
        byte[] bytes = listSerializer.toBytes(new ArrayList<>());
        List<BytesSerializable> res = listSerializer.parseBytesTry(bytes).get();
        assertEquals("Deserialized list should by empty", 0, res.size());

        // Test 2: not empty list with different types.
        ArrayList<BytesSerializable> data = new ArrayList<>();
        data.add(new ListSerializerTestObjectA("test1"));
        data.add(new ListSerializerTestObjectA("test2"));
        data.add(new ListSerializerTestObjectB(1));
        data.add(new ListSerializerTestObjectA("test3"));
        data.add(new ListSerializerTestObjectB(2));
        data.add(new ListSerializerTestObjectB(3));

        bytes = listSerializer.toBytes(data);
        res = listSerializer.parseBytesTry(bytes).get();

        assertEquals("Deserialized list has different size than original", data.size(), res.size());
        for(int i = 0; i < data.size(); i++)
            assertEquals("Deserialized list is different to original", data.get(i), res.get(i));
    }

    @Test
    public void ListSerializerTest_FailureSerializationTestForSingleType() {

        ListSerializer<BytesSerializable> listSerializerWithLimits = new ListSerializer<>((ScorexSerializer)new ListSerializerTestObjectASerializer(), 2);
        ListSerializer<BytesSerializable> listSerializer = new ListSerializer<>((ScorexSerializer)new ListSerializerTestObjectASerializer());

        ArrayList<BytesSerializable> data = new ArrayList<>();
        data.add(new ListSerializerTestObjectA("test1"));
        data.add(new ListSerializerTestObjectA("test2"));

        // Test 1: bytes not broken, list size in NOT upper the limit
        boolean exceptionOccurred = false;
        byte[] bytes = listSerializerWithLimits.toBytes(data);
        try {
            listSerializerWithLimits.parseBytesTry(bytes).get();
        }
        catch (Exception e) {
            exceptionOccurred = true;
        }
        assertFalse("List expected to be deserialized successful", exceptionOccurred);


        // Test 2: bytes not broken, list size in upper the limit
        exceptionOccurred = false;
        data.add(new ListSerializerTestObjectA("test3"));
        bytes = listSerializer.toBytes(data);
        try {
            listSerializerWithLimits.parseBytesTry(bytes).get();
        }
        catch (Exception e) {
            exceptionOccurred = true;
        }
        assertTrue("Exception expected during deserialization, because of list size limits.", exceptionOccurred);


        // Test 3: serialization, list size in upper the limit
        exceptionOccurred = false;
        try {
            bytes = listSerializer.toBytes(data);
            listSerializerWithLimits.parseBytesTry(bytes).get();
        }
        catch (Exception e) {
            exceptionOccurred = true;
        }
        assertTrue("Exception expected during serialization, because of list size limits.", exceptionOccurred);

        // Test 4: broken bytes: contains some garbage in the end
        exceptionOccurred = false;
        bytes = new byte[]{ 10, 0, 0, 0, 1};
        try {
            listSerializerWithLimits.parseBytesTry(bytes).get();
        }
        catch (Exception e) {
            exceptionOccurred = true;
        }
        assertTrue("Exception expected during deserialization, because of garbage.", exceptionOccurred);


        // Test 5: broken bytes: some bytes in the end were cut
        bytes = listSerializer.toBytes(data);
        bytes = Arrays.copyOfRange(bytes, 0, bytes.length - 1);
        exceptionOccurred = false;
        try {
            listSerializer.parseBytesTry(bytes).get();
        }
        catch (Exception e) {
            exceptionOccurred = true;
        }
        assertTrue("Exception expected during deserialization, because of cut end of bytes.", exceptionOccurred);


        // Test 6: broken bytes passed
        exceptionOccurred = false;
        bytes = new byte[]{ 10, 0, 0, 2, 1, 0, 3, 0, 1, 0, 4, 5, 6};
        try {
            listSerializerWithLimits.parseBytesTry(bytes).get();
        }
        catch (Exception e) {
            exceptionOccurred = true;
        }
        assertTrue("Exception expected during deserialization, because of garbage.", exceptionOccurred);
    }

    @Test
    public void ListSerializerTest_FailureSerializationTestForMultipleTypes() {
        DynamicTypedSerializer serializersCompanion = new DynamicTypedSerializer<>(
                new HashMap<Byte, ScorexSerializer<BytesSerializable>>() {{
                    put((byte)1, (ScorexSerializer)new ListSerializerTestObjectASerializer());
                    put((byte)2, (ScorexSerializer)new ListSerializerTestObjectBSerializer());
                }}, new HashMap<>()
        );

        ListSerializer<BytesSerializable> listSerializerWithLimits = new ListSerializer<>(serializersCompanion, 2);
        ListSerializer<BytesSerializable> listSerializer = new ListSerializer<>(serializersCompanion);

        ArrayList<BytesSerializable> data = new ArrayList<>();
        data.add(new ListSerializerTestObjectA("test1"));
        data.add(new ListSerializerTestObjectB(1));

        // Test 1: bytes not broken, list size in NOT upper the limit
        boolean exceptionOccurred = false;
        byte[] bytes = listSerializerWithLimits.toBytes(data);
        try {
            listSerializerWithLimits.parseBytesTry(bytes).get();
        }
        catch (Exception e) {
            System.out.println(e);
            exceptionOccurred = true;
        }
        assertFalse("List expected to be deserialized successful", exceptionOccurred);

        // Test 2: bytes not broken, list size in upper the limit
        exceptionOccurred = false;
        data.add(new ListSerializerTestObjectA("test2"));
        bytes = listSerializer.toBytes(data);
        try {
            listSerializerWithLimits.parseBytesTry(bytes).get();
        }
        catch (Exception e) {
            exceptionOccurred = true;
        }
        assertTrue("Exception expected during deserialization, because of list size limits.", exceptionOccurred);

        // Test 3: serialization, list size in upper the limit
        exceptionOccurred = false;
        data.add(new ListSerializerTestObjectA("test2"));
        try {
            bytes = listSerializerWithLimits.toBytes(data);
            listSerializerWithLimits.parseBytesTry(bytes).get();
        }
        catch (Exception e) {
            exceptionOccurred = true;
        }
        assertTrue("Exception expected during deserialization, because of list size limits.", exceptionOccurred);

        // Test 4: broken bytes: contains some garbage in the end
        exceptionOccurred = false;
        bytes = new byte[]{ 10, 0, 0, 0, 1};
        try {
            listSerializerWithLimits.parseBytesTry(bytes).get();
        }
        catch (Exception e) {
            exceptionOccurred = true;
        }
        assertTrue("Exception expected during deserialization, because of garbage.", exceptionOccurred);


        // Test 5: broken bytes: some bytes in the end were cut
        bytes = listSerializer.toBytes(data);
        bytes = Arrays.copyOfRange(bytes, 0, bytes.length - 1);
        exceptionOccurred = false;
        try {
            listSerializer.parseBytesTry(bytes).get();
        }
        catch (Exception e) {
            exceptionOccurred = true;
        }
        assertTrue("Exception expected during deserialization, because of cut end of bytes.", exceptionOccurred);


        // Test 6: broken bytes passed
        exceptionOccurred = false;
        bytes = new byte[]{ 10, 0, 0, 2, 1, 0, 3, 0, 1, 0, 4, 5, 6};
        try {
            listSerializerWithLimits.parseBytesTry(bytes).get();
        }
        catch (Exception e) {
            exceptionOccurred = true;
        }
        assertTrue("Exception expected during deserialization, because of garbage.", exceptionOccurred);
    }
}