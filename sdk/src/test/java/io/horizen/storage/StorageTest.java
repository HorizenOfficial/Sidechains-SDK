package io.horizen.storage;

import io.horizen.fixtures.StoreFixtureClass;
import io.horizen.fork.ForkManagerUtil;
import io.horizen.fork.SimpleForkConfigurator;
import io.horizen.storage.leveldb.VersionedLevelDbStorageAdapter;
import io.horizen.utils.ByteArrayWrapper;

import java.io.File;
import java.util.*;
import io.horizen.utils.Pair;

import org.junit.*;

import static org.junit.Assert.*;

public class StorageTest {

    StoreFixtureClass storageFixture = new StoreFixtureClass();

    @Before
    public void init() {
        ForkManagerUtil.initializeForkManager(new SimpleForkConfigurator(), "regtest");
    }

    @Test
    public void testStorage() {
        VersionedLevelDbStorageAdapter s = storageFixture.getStorage();

        //Test empty storage
        assertTrue("Storage expected to be empty.", s.isEmpty());
        assertFalse("Version of empty storage shall be none", s.lastVersionID().isPresent());

        //Test updating storage with empty values
        ByteArrayWrapper version0 = storageFixture.getVersion();
        List<Pair<ByteArrayWrapper,ByteArrayWrapper>> u0 = new ArrayList<>();
        s.update(version0, u0, new ArrayList<>());
        List<Pair<ByteArrayWrapper, ByteArrayWrapper>> keysAfterFirstUpdate = s.getAll();
        assertEquals("No keys are expected after update with empty values", 0, keysAfterFirstUpdate.size());
        assertEquals("last version shall be as expected after empty update", version0, s.lastVersionID().get());

        //Test updating storage with non-empty values
        int firstUpdateSize = 3;
        ByteArrayWrapper version1 = storageFixture.getVersion();
        List<Pair<ByteArrayWrapper,ByteArrayWrapper>> u1 = storageFixture.getKeyValueList(firstUpdateSize);
        s.update(version1, u1, new ArrayList<>());

        assertFalse("Storage expected to be not empty.", s.isEmpty());
        assertEquals("Storage must contain 3 items.", firstUpdateSize, s.getAll().size());
        assertEquals("Storage must contain value for key - " + u1.get(0).getKey(), u1.get(0).getValue(), s.get(u1.get(0).getKey()).get());
        assertTrue("Storage must contain same elements as sample.", u1.containsAll(s.getAll()));
        assertEquals("Storage must have specified last version.", version1, s.lastVersionID().get());
        assertEquals("Storage must have two versions", 2, s.rollbackVersions().size());
        assertTrue("Storage must have specified versions", s.rollbackVersions().containsAll(Arrays.asList(version0, version1)));

        //Test replacing values in storage to empty values
        ByteArrayWrapper version2 = storageFixture.getVersion();
        List<Pair<ByteArrayWrapper,ByteArrayWrapper>> u2 = new ArrayList<>();

        for (Pair<ByteArrayWrapper,ByteArrayWrapper> i : u1)
            u2.add(new Pair<>(i.getKey(), storageFixture.getValue()));

        s.update(version2, u2, new ArrayList<>());

        assertEquals("Storage must contain 3 items.", 3, s.getAll().size());
        assertEquals("Storage must contain value for key - " + u2.get(0).getKey(), u2.get(0).getValue(), s.get(u2.get(0).getKey()).get());
        assertTrue("Storage must contain same elements as sample.", u2.containsAll(s.getAll()));
        assertEquals("Storage must have specified version.", version2, s.lastVersionID().get());
        assertEquals("Storage must have three versions.", 3, s.rollbackVersions().size());
        assertTrue("Storage must have specified versions", s.rollbackVersions().containsAll(Arrays.asList(version0, version1, version2)));


        //Test partial replacing values in storage
        ByteArrayWrapper version3 = storageFixture.getVersion();
        List<ByteArrayWrapper> r = new ArrayList<>();

        for (Pair<ByteArrayWrapper,ByteArrayWrapper> i : u2.subList(1,3))
            r.add(i.getKey());

        s.update(version3, new ArrayList<>(), r);


        assertEquals("Storage must contain 1 item.", 1, s.getAll().size());
        assertEquals("Storage must contain value for key - " + u2.get(0).getKey(), u2.get(0).getValue(), s.get(u2.get(0).getKey()).get());
        assertFalse("Storage must not contain removed elements.", s.getAll().contains(u2.get(1)));
        assertFalse("Storage must not contain removed elements.", s.getAll().contains(u2.get(2)));

        assertEquals("Storage must have specified version.", version3, s.lastVersionID().get());
        assertEquals("Storage must have four versions.", 4, s.rollbackVersions().size());

        //Test storage rollback
        s.rollback(version2);

        assertEquals("Storage must contain 3 items.", 3, s.getAll().size());
        assertEquals("Storage must contain value for key - " + u2.get(0).getKey(), u2.get(0).getValue(), s.get(u2.get(0).getKey()).get());
        assertTrue("Storage must contain same elements as sample.", u2.containsAll(s.getAll()));
        assertEquals("Storage must have specified version.", version2, s.lastVersionID().get());
        assertEquals("Storage must have two versions.", 3, s.rollbackVersions().size());

        //Test adding/removing duplicated values
        try {
            List<ByteArrayWrapper> duplicatedRemove = new ArrayList<>(r);
            duplicatedRemove.add(r.get(0));

            ByteArrayWrapper duplicatedRemoveVersion = storageFixture.getVersion();
            s.update(duplicatedRemoveVersion, new ArrayList<>(), duplicatedRemove);
            fail("The same value in remove shall thrown exception");
        }
        catch (Exception ex) {
            assertEquals("Storage must have specified version.", version2, s.lastVersionID().get());
        }

        try {
            List<Pair<ByteArrayWrapper,ByteArrayWrapper>> duplicatedUpdated = new ArrayList<>(u2);
            duplicatedUpdated.add(u2.get(0));

            ByteArrayWrapper duplicatedUpdatedVersion = storageFixture.getVersion();
            s.update(duplicatedUpdatedVersion, duplicatedUpdated, new ArrayList<>());
            fail("The same value in update shall thrown exception");
        }
        catch (Exception ex) {
            assertEquals("Storage must have specified version.", version2, s.lastVersionID().get());
        }

        //Try to update with already exist version
        try {
            s.update(version2, u2, new ArrayList<>());
            fail("already exist version in update shall thrown exception");
        }
        catch (Exception ex) {
            assertEquals("Storage must have specified version.", version2, s.lastVersionID().get());
        }

        //Try to update to non exist version
        try {
            s.rollback(storageFixture.getVersion());
            fail("Non exist version in rollback shall thrown exception");
        }
        catch (Exception ex) {
            assertEquals("Storage must have specified version.", version2, s.lastVersionID().get());
        }



        //Try to remove non-exist values
        List<Pair<ByteArrayWrapper,ByteArrayWrapper>> nonExistValues = storageFixture.getKeyValueList(5);
        ByteArrayWrapper nonExistValuesVersion = storageFixture.getVersion();
        s.update(nonExistValuesVersion, new ArrayList<>(), Collections.singletonList(storageFixture.getKeyValue().getKey()));
        assertEquals("Storage must have specified version.", nonExistValuesVersion, s.lastVersionID().get());
    }

    @Test
    public void reopenStorage() {
        File storagePath = storageFixture.tempFile();
        VersionedLevelDbStorageAdapter s = storageFixture.getStorage(storagePath);
        assertTrue("Storage expected to be empty.", s.isEmpty());

        assertFalse("Version of empty storage shall be none", s.lastVersionID().isPresent());

        ByteArrayWrapper version0 = storageFixture.getVersion();
        List<Pair<ByteArrayWrapper,ByteArrayWrapper>> u0 = new ArrayList<>();
        s.update(version0, u0, new ArrayList<>());
        List<Pair<ByteArrayWrapper, ByteArrayWrapper>> keysAfterFirstUpdate = s.getAll();
        assertEquals("No keys are expected after update with empty values", 0, keysAfterFirstUpdate.size());
        assertEquals("last version shall be as expected after empty update", version0, s.lastVersionID().get());

        int firstUpdateSize = 3;
        ByteArrayWrapper version1 = storageFixture.getVersion();
        List<Pair<ByteArrayWrapper,ByteArrayWrapper>> u1 = storageFixture.getKeyValueList(firstUpdateSize);
        s.update(version1, u1, new ArrayList<>());

        assertFalse("Storage expected to be not empty.", s.isEmpty());
        assertEquals("Storage must contain 3 items.", firstUpdateSize, s.getAll().size());
        assertEquals("Storage must contain value for key - " + u1.get(0).getKey(), u1.get(0).getValue(), s.get(u1.get(0).getKey()).get());
        assertTrue("Storage must contain same elements as sample.", u1.containsAll(s.getAll()));
        assertEquals("Storage must have specified last version.", version1, s.lastVersionID().get());
        assertEquals("Storage must have two versions", 2, s.rollbackVersions().size());
        assertTrue("Storage must have specified versions", s.rollbackVersions().containsAll(Arrays.asList(version0, version1)));

        s.close();

        VersionedLevelDbStorageAdapter s2 = storageFixture.getStorage(storagePath);
        assertFalse("Storage expected to be not empty.", s2.isEmpty());
        assertEquals("Storage must contain 3 items.", firstUpdateSize, s2.getAll().size());
        assertEquals("Storage must contain value for key - " + u1.get(0).getKey(), u1.get(0).getValue(), s2.get(u1.get(0).getKey()).get());
        assertTrue("Storage must contain same elements as sample.", u1.containsAll(s2.getAll()));
        assertEquals("Storage must have specified last version.", version1, s2.lastVersionID().get());
        assertEquals("Storage must have two versions", 2, s2.rollbackVersions().size());
        assertTrue("Storage must have specified versions", s2.rollbackVersions().containsAll(Arrays.asList(version0, version1)));
    }
}