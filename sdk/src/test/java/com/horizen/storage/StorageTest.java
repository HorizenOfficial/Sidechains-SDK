package com.horizen.storage;

import com.horizen.fixtures.IODBStoreFixtureClass;
import com.horizen.utils.ByteArrayWrapper;

import java.util.*;
import com.horizen.utils.Pair;

import org.junit.*;
import static org.junit.Assert.*;

public class StorageTest {

    IODBStoreFixtureClass storageFixture = new IODBStoreFixtureClass();

    @Test
    public void testStorage() {
        Storage s = new IODBStoreAdapter(storageFixture.getStore());
        assertTrue("Storage expected to be empty.", s.isEmpty());

        ByteArrayWrapper version1 = storageFixture.getVersion();
        List<Pair<ByteArrayWrapper,ByteArrayWrapper>> u1 = storageFixture.getKeyValueList(3);

        s.update(version1, u1, new ArrayList<ByteArrayWrapper>());

        assertFalse("Storage expected to be not empty.", s.isEmpty());
        assertEquals("Storage must contain 3 items.", 3, s.getAll().size());
        assertEquals("Storage must contain value for key - " + u1.get(0).getKey(), u1.get(0).getValue(), s.get(u1.get(0).getKey()).get());
        assertTrue("Storage must contain same elements as sample.", u1.containsAll(s.getAll()));
        assertEquals("Storage must have specified last version.", version1, s.lastVersionID().get());
        assertEquals("Storage must have only one version.", 1, s.rollbackVersions().size());
        assertTrue("Storage must have only specified version.", s.rollbackVersions().contains(version1));

        ByteArrayWrapper version2 = storageFixture.getVersion();
        List<Pair<ByteArrayWrapper,ByteArrayWrapper>> u2 = new ArrayList<>();

        for (Pair<ByteArrayWrapper,ByteArrayWrapper> i : u1)
            u2.add(new Pair<ByteArrayWrapper,ByteArrayWrapper>(i.getKey(), storageFixture.getValue()));

        s.update(version2, u2, new ArrayList<ByteArrayWrapper>());

        assertEquals("Storage must contain 3 items.", 3, s.getAll().size());
        assertEquals("Storage must contain value for key - " + u2.get(0).getKey(), u2.get(0).getValue(), s.get(u2.get(0).getKey()).get());
        assertTrue("Storage must contain same elements as sample.", u2.containsAll(s.getAll()));
        assertEquals("Storage must have specified version.", version2, s.lastVersionID().get());
        assertEquals("Storage must have two versions.", 2, s.rollbackVersions().size());
        assertTrue("Storage must contain specified version.", s.rollbackVersions().contains(version1));
        assertTrue("Storage must contain specified version.", s.rollbackVersions().contains(version2));

        ByteArrayWrapper version3 = storageFixture.getVersion();
        List<ByteArrayWrapper> r = new ArrayList<>();

        for (Pair<ByteArrayWrapper,ByteArrayWrapper> i : u2.subList(1,3))
            r.add(i.getKey());

        s.update(version3, new ArrayList<Pair<ByteArrayWrapper,ByteArrayWrapper>>(), r);

        assertEquals("Storage must contain 1 item.", 1, s.getAll().size());
        assertEquals("Storage must contain value for key - " + u2.get(0).getKey(), u2.get(0).getValue(), s.get(u2.get(0).getKey()).get());
        assertFalse("Storage must not contain removed elements.", s.getAll().containsAll(u2.subList(1,3)));
        assertEquals("Storage must have specified version.", s.lastVersionID().get(), version3);
        assertEquals("Storage must have three versions.", s.rollbackVersions().size(), 3);

        s.rollback(version2);

        assertEquals("Storage must contain 3 items.", 3, s.getAll().size());
        assertEquals("Storage must contain value for key - " + u2.get(0).getKey(), u2.get(0).getValue(), s.get(u2.get(0).getKey()).get());
        assertTrue("Storage must contain same elements as sample.", u2.containsAll(s.getAll()));
        assertEquals("Storage must have specified version.", version2, s.lastVersionID().get());
        assertEquals("Storage must have two versions.", 2, s.rollbackVersions().size());
    }
}