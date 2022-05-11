package com.horizen.storage;

import com.horizen.fixtures.StoreNewFixtureClass;
import com.horizen.fixtures.StoreNewFixture;
import com.horizen.storage.rocksdb.VersionedRocksDbStorageAdapter;
import com.horizen.storage.rocksdb.VersionedRocksDbStorageNewAdapter;
import com.horizen.storage.rocksdb.VersionedRocksDbViewAdapter;
import com.horizen.utils.ByteArrayWrapper;
import com.horizen.utils.BytesUtils;
import com.horizen.utils.Pair;
import org.junit.Test;

import java.io.File;
import java.sql.Array;
import java.util.*;

import static org.junit.Assert.*;

public class StorageNewTest {

    StoreNewFixtureClass storageFixture = new StoreNewFixtureClass();

    @Test
    public void testStorage() {
        VersionedRocksDbStorageNewAdapter s = storageFixture.getStorage();

        //Test empty storage
        assertTrue("Storage expected to be empty.", s.isEmpty());
        assertFalse("Version of empty storage shall be none", s.lastVersionID().isPresent());

        //Test updating storage with empty values
        ByteArrayWrapper version0 = storageFixture.getVersion();
        HashMap<byte[], byte[]> u0 = new HashMap<>();

        VersionedRocksDbViewAdapter view0 = (VersionedRocksDbViewAdapter) s.getView();
        view0.update(u0, new java.util.HashSet<>());
        view0.commit(version0);

        java.util.Map<byte[], byte[]> keysAfterFirstUpdate = s.getAll();
        assertEquals("No keys are expected after update with empty values", 0, keysAfterFirstUpdate.size());
        assertEquals("last version shall be as expected after empty update", version0, s.lastVersionID().get());

        //Test updating storage with non-empty values
        int firstUpdateSize = 3;
        ByteArrayWrapper version1 = storageFixture.getVersion();
        java.util.Map<byte[], byte[]> u1 = storageFixture.getKeyValueList(firstUpdateSize);
        java.util.Map.Entry<byte[], byte[]> firstEntryU1 = u1.entrySet().iterator().next();

        StorageVersionedView view1 = s.getView();
        view1.update(u1, new java.util.HashSet<>());
        view1.commit(version1);

        assertFalse("Storage expected to be not empty.", s.isEmpty());
        assertEquals("Storage must contain 3 items.", firstUpdateSize, s.getAll().size());
        assertEquals("Storage must contain value for key - " + BytesUtils.toHexString(firstEntryU1.getKey()),
                BytesUtils.toHexString(firstEntryU1.getValue()), BytesUtils.toHexString(s.get(firstEntryU1.getKey())));

        assertTrue("Storage must contain same elements as sample.", storageFixture.compareMaps(u1, s.getAll()));

        assertEquals("Storage must have specified last version.", version1, s.lastVersionID().get());
        assertEquals("Storage must have two versions", 2, s.rollbackVersions().size());
        assertTrue("Storage must have specified versions", s.rollbackVersions().containsAll(Arrays.asList(version0, version1)));

        //Test replacing values in storage to empty values
        ByteArrayWrapper version2 = storageFixture.getVersion();
        java.util.Map<byte[], byte[]> u2 = new HashMap<>();

        for (java.util.Map.Entry<byte[], byte[]> entry: u1.entrySet())
            u2.put(entry.getKey(), storageFixture.getValue());

        StorageVersionedView view2 = s.getView();
        view2.update(u2, new java.util.HashSet<>());
        view2.commit(version2);

        java.util.Map.Entry<byte[], byte[]>  firstEntryU2 = u2.entrySet().iterator().next();

        assertEquals("Storage must contain 3 items.", 3, s.getAll().size());
        assertTrue("Storage must contain value for key - " + BytesUtils.toHexString(firstEntryU2.getKey()),
                storageFixture.compareValues(firstEntryU2.getValue(), s.get(firstEntryU2.getKey())));
        assertTrue("Storage must contain same elements as sample.", storageFixture.compareMaps(u2, s.getAll()));
        assertEquals("Storage must have specified version.", version2, s.lastVersionID().get());
        assertEquals("Storage must have three versions.", 3, s.rollbackVersions().size());
        assertTrue("Storage must have specified versions", s.rollbackVersions().containsAll(Arrays.asList(version0, version1, version2)));


        //Test partial replacing values in storage
        ByteArrayWrapper version3 = storageFixture.getVersion();
        java.util.Set<byte[]> keptElements = new java.util.HashSet<>();
        java.util.Set<byte[]> removedElements = new java.util.HashSet<>();

        int count = 0;
        for (java.util.Map.Entry<byte[], byte[]> entry: u2.entrySet())
        {
            count++;
            if (count == 1 || count >= 3)
            {
                keptElements.add(entry.getKey());
            } else {
                removedElements.add(entry.getKey());
            }
         }

        StorageVersionedView view3 = s.getView();
        view3.update(new java.util.HashMap<>(), removedElements);
        view3.commit(version3);

        assertEquals("Storage must contain 2 item.", 2, s.getAll().size());
        Iterator<byte[]> it = keptElements.iterator();
        while (it.hasNext()) {
            byte[] entry = it.next();
            assertTrue("Storage must contain value for key - " + BytesUtils.toHexString(entry), storageFixture.mapContainsKey(s.getAll(), entry));
        }
        it = removedElements.iterator();
        while (it.hasNext()) {
            byte[] entry = it.next();
            assertFalse("Storage must not contain value for key - " + BytesUtils.toHexString(entry), storageFixture.mapContainsKey(s.getAll(), entry));
        }

        assertEquals("Storage must have specified version.", version3, s.lastVersionID().get());
        assertEquals("Storage must have 4 versions.", 4, s.rollbackVersions().size());

        //Test storage rollback
        s.rollback(version2);

        assertEquals("Storage must contain 3 items.", 3, s.getAll().size());
        assertTrue("Storage must contain value for key - " + BytesUtils.toHexString(firstEntryU2.getKey()),
                storageFixture.compareValues(firstEntryU2.getValue(), s.get(firstEntryU2.getKey())));

        assertTrue("Storage must contain same elements as sample.", storageFixture.compareMaps(u2, s.getAll()));
        assertEquals("Storage must have specified version.", version2, s.lastVersionID().get());
        assertEquals("Storage must have 3 versions.", 3, s.rollbackVersions().size());

        // try to get a view on an older version, it is ok but commit is not possible
        ByteArrayWrapper version2_1 = storageFixture.getVersion();
        Optional<StorageVersionedView> view2_1 = s.getView(version1);
        assertFalse("View on version should not be empty", view2_1.isEmpty());
        view2_1.get().update(new java.util.HashMap<>(), removedElements);

        try {
            view2_1.get().commit(version2_1);
            fail("already exist version in update shall thrown exception");
        }
        catch (java.lang.Exception ex) {
            assertEquals("Transaction for a previous version of a StorageVersioned can't be committed", version2, s.lastVersionID().get());
        }

        byte[] key1 = {1, 2, 3};
        byte[] key2 = {1, 2, 3};
        Map<byte[], String> map = new HashMap<>();
        map.put(key1, "value1");
        map.put(key2, "value2");

        String retrievedValue1 = map.get(key1);
        String retrievedValue2 = map.get(key2);
        String retrievedValue3 = map.get(new byte[]{1, 2, 3});

        assertTrue("qqq", retrievedValue1.equals("value1"));
        assertTrue("www", retrievedValue2.equals("value2"));
        assertTrue("eee", retrievedValue3 == null);

        // try to add twice the same array in a set of bytes[] (does the hashing work?)
        java.util.Map<byte[], byte[]> u4 = new HashMap<>();
        ByteArrayWrapper baw1 = new ByteArrayWrapper(storageFixture.getValue());
        ByteArrayWrapper baw2 = new ByteArrayWrapper(baw1.data());


        u4.put(baw1.data(), storageFixture.getValue());
        u4.put(baw2.data(), storageFixture.getValue());

        ByteArrayWrapper version4 = storageFixture.getVersion();
        StorageVersionedView view4 = s.getView();
        view4.update(u4, new java.util.HashSet<>());
        view4.commit(version4);
        //Try to update with latest version
 /*
        try {
            s.update(version2, u2,  new java.util.HashSet<>());
            fail("already exist version in update shall thrown exception");
        }
        catch (Exception ex) {
            assertEquals("Storage should not allow updating with an already contained version.", version2, s.lastVersionID().get());
        }

        //Try to update with a version already stored previously (not latest)
        try {
            s.update(version1, u2,  new java.util.HashSet<>());
            fail("already exist version in update shall thrown exception");
        }
        catch (Exception ex) {
            assertTrue("Storage already has the specified version.", s.rollbackVersions().contains(version1));
        }

        //Try to update to non exist version
        try {
            s.rollback(storageFixture.getVersion());
            fail("Non exist version in rollback shall thrown exception");
        }
        catch (Exception ex) {
            assertEquals("Storage must have specified version.", version2, s.lastVersionID().get());
        }

        //Try to remove non-exist values, it is allowed
        java.util.Set<byte[]> randomValues = storageFixture.getValueList(5);

        ByteArrayWrapper nonExistValuesVersion = storageFixture.getVersion();
        try {
            s.update(nonExistValuesVersion, new java.util.HashMap<>(), randomValues);
        }
        catch (Exception ex) {
            fail("It should be possible to update by removing not-existing values: " + ex.getMessage());
        }
        assertEquals("Storage must have specified version.", nonExistValuesVersion, s.lastVersionID().get());
*/
    }

    @Test
    public void reopenStorage() {
        /*
        File storagePath = storageFixture.tempFile();
        VersionedRocksDbStorageNewAdapter s = storageFixture.getStorage(storagePath);
        assertTrue("Storage expected to be empty.", s.isEmpty());

        assertFalse("Version of empty storage shall be none", s.lastVersionID().isPresent());

        ByteArrayWrapper version0 = storageFixture.getVersion();
        HashMap<byte[], byte[]> u0 = new HashMap<>();
        s.update(version0, u0, new java.util.HashSet<>());

        java.util.Map<byte[], byte[]> keysAfterFirstUpdate = s.getAll();
        assertEquals("No keys are expected after update with empty values", 0, keysAfterFirstUpdate.size());
        assertEquals("last version shall be as expected after empty update", version0, s.lastVersionID().get());

        int firstUpdateSize = 3;
        ByteArrayWrapper version1 = storageFixture.getVersion();
        Map<byte[], byte[]> u1 = storageFixture.getKeyValueList(firstUpdateSize);
        java.util.Map.Entry<byte[], byte[]> firstEntryU1 = u1.entrySet().iterator().next();
        s.update(version1, u1, new java.util.HashSet<>());

        assertFalse("Storage expected to be not empty.", s.isEmpty());
        assertEquals("Storage must contain 3 items.", firstUpdateSize, s.getAll().size());
        assertEquals("Storage must contain value for key - " + BytesUtils.toHexString(firstEntryU1.getKey()),
                BytesUtils.toHexString(firstEntryU1.getValue()), BytesUtils.toHexString(s.get(firstEntryU1.getKey()).get()));
        assertTrue("Storage must contain same elements as sample.", storageFixture.compareMaps(u1, s.getAll()));
        assertEquals("Storage must have specified last version.", version1, s.lastVersionID().get());
        assertEquals("Storage must have two versions", 2, s.rollbackVersions().size());
        assertTrue("Storage must have specified versions", s.rollbackVersions().containsAll(Arrays.asList(version0, version1)));

        s.close(); // it frees resources too

        VersionedRocksDbStorageNewAdapter s2 = storageFixture.getStorage(storagePath);
        assertEquals("Storage must contain 3 items.", firstUpdateSize, s2.getAll().size());
        assertEquals("Storage must contain value for key - " + BytesUtils.toHexString(firstEntryU1.getKey()),
                BytesUtils.toHexString(firstEntryU1.getValue()), BytesUtils.toHexString(s2.get(firstEntryU1.getKey()).get()));
        assertTrue("Storage must contain same elements as sample.", storageFixture.compareMaps(u1, s2.getAll()));
        assertEquals("Storage must have specified last version.", version1, s2.lastVersionID().get());
        assertEquals("Storage must have two versions", 2, s2.rollbackVersions().size());
        assertTrue("Storage must have specified versions", s2.rollbackVersions().containsAll(Arrays.asList(version0, version1)));
    */
    }
}
