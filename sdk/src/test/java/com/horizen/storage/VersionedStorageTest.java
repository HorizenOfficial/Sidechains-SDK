package com.horizen.storage;

import com.horizen.fixtures.VersionedStorageFixtureClass;
import com.horizen.storage.rocksdb.VersionedRocksDbStorageAdapter;
import com.horizen.storage.rocksdb.VersionedRocksDbViewAdapter;
import com.horizen.utils.ByteArrayWrapper;
import com.horizen.utils.BytesUtils;
import com.horizen.utils.Pair;
import org.junit.Test;

import java.io.File;
import java.util.*;

import static org.junit.Assert.*;

public class VersionedStorageTest {

    VersionedStorageFixtureClass storageFixture = new VersionedStorageFixtureClass();

    @Test
    public void testStorage() {
        VersionedRocksDbStorageAdapter s = storageFixture.getStorage();

        //Test empty storage
        assertTrue("Storage expected to be empty.", s.isEmpty());
        assertFalse("Version of empty storage shall be none", s.lastVersionID().isPresent());

        //Test updating storage with empty values
        ByteArrayWrapper version0 = storageFixture.getVersion();
        java.util.ArrayList<Pair<byte[], byte[]>> u0 = new ArrayList<>();

        VersionedRocksDbViewAdapter view0 = (VersionedRocksDbViewAdapter) s.getView();
        view0.update(u0, new java.util.ArrayList<>());
        view0.commit(version0);

        java.util.List<Pair<byte[], byte[]>> keysAfterFirstUpdate = s.getAll();
        assertEquals("No keys are expected after update with empty values", 0, keysAfterFirstUpdate.size());
        assertEquals("last version shall be as expected after empty update", version0, s.lastVersionID().get());

        //Test updating storage with non-empty values
        int firstUpdateSize = 3;
        ByteArrayWrapper version1 = storageFixture.getVersion();
        java.util.List<Pair<byte[], byte[]>> u1 = storageFixture.getKeyValueList(firstUpdateSize);
        Pair<byte[], byte[]> firstEntryU1 = u1.get(0);

        VersionedStorageView view1 = s.getView();
        view1.update(u1, new java.util.ArrayList<>());
        view1.commit(version1);

        assertFalse("Storage expected to be not empty.", s.isEmpty());
        assertEquals("Storage must contain 3 items.", firstUpdateSize, s.getAll().size());
        assertEquals("Storage must contain value for key - " + BytesUtils.toHexString(firstEntryU1.getKey()),
                BytesUtils.toHexString(firstEntryU1.getValue()), BytesUtils.toHexString(s.get(firstEntryU1.getKey())));

        assertTrue("Storage must contain same elements as sample.", storageFixture.listContainment(u1, s.getAll()));

        assertEquals("Storage must have specified last version.", version1, s.lastVersionID().get());
        assertEquals("Storage must have two versions", 2, s.rollbackVersions().size());
        assertTrue("Storage must have specified versions", s.rollbackVersions().containsAll(Arrays.asList(version0, version1)));

        //Test replacing values in storage to new values
        ByteArrayWrapper version2 = storageFixture.getVersion();
        java.util.ArrayList<Pair<byte[], byte[]>> u2 = new ArrayList<>();

        for (Pair<byte[], byte[]> entry : u1)
            u2.add(new Pair<>(entry.getKey(), storageFixture.getValue()));

        VersionedStorageView view2 = s.getView();
        view2.update(u2, new java.util.ArrayList<>());
        view2.commit(version2);

        Pair<byte[], byte[]> firstEntryU2 = u2.get(0);

        assertEquals("Storage must contain 3 items.", 3, s.getAll().size());
        assertTrue("Storage must contain value for key - " + BytesUtils.toHexString(firstEntryU2.getKey()),
                storageFixture.compareValues(firstEntryU2.getValue(), s.get(firstEntryU2.getKey())));
        assertTrue("Storage must contain same elements as sample.", storageFixture.listContainment(u2, s.getAll()));
        assertEquals("Storage must have specified version.", version2, s.lastVersionID().get());
        assertEquals("Storage must have three versions.", 3, s.rollbackVersions().size());
        assertTrue("Storage must have specified versions", s.rollbackVersions().containsAll(Arrays.asList(version0, version1, version2)));

        //Test partial replacing values in storage
        ByteArrayWrapper version3 = storageFixture.getVersion();
        List<byte[]> removedElements = new ArrayList<>();

        for (Pair<byte[], byte[]> i : u2.subList(1, 3))
            removedElements.add(i.getKey());

        VersionedStorageView view3 = s.getView();
        view3.update(new ArrayList<>(), removedElements);
        view3.commit(version3);

        assertEquals("Storage must contain 1 item.", 1, s.getAll().size());
        assertTrue("Storage must contain value for key - " + BytesUtils.toHexString(u2.get(0).getKey()),
                storageFixture.compareValues(u2.get(0).getValue(), s.get(u2.get(0).getKey())));
        assertFalse("Storage must not contain removed elements.",
                storageFixture.compareValues(u2.get(1).getValue(), s.get(u2.get(1).getKey())));
        assertFalse("Storage must not contain removed elements.",
                storageFixture.compareValues(u2.get(2).getValue(), s.get(u2.get(2).getKey())));
        assertEquals("Storage must have specified version.", version3, s.lastVersionID().get());
        assertEquals("Storage must have 4 versions.", 4, s.rollbackVersions().size());

        //Test storage rollback
        s.rollback(version2);

        assertEquals("Storage must contain 3 items.", 3, s.getAll().size());
        assertTrue("Storage must contain value for key - " + BytesUtils.toHexString(firstEntryU2.getKey()),
                storageFixture.compareValues(firstEntryU2.getValue(), s.get(firstEntryU2.getKey())));

        assertTrue("Storage must contain same elements as sample.", storageFixture.listContainment(u2, s.getAll()));
        assertEquals("Storage must have specified version.", version2, s.lastVersionID().get());
        assertEquals("Storage must have 3 versions.", 3, s.rollbackVersions().size());

        // try to get a view on an older version, it is ok but commit is not possible
        ByteArrayWrapper version2_1 = storageFixture.getVersion();
        Optional<VersionedStorageView> view2_1 = s.getView(version1);
        assertFalse("View on version should not be empty", view2_1.isEmpty());
        view2_1.get().update(new ArrayList<>(), removedElements);

        try {
            view2_1.get().commit(version2_1);
            fail("Transaction for a previous version of a StorageVersioned can't be committed");
        } catch (java.lang.Exception ex) {
            assertEquals("Transaction for a previous version of a StorageVersioned can't be committed", version2, s.lastVersionID().get());
        }

        // try to add twice the same key in a list of bytes[] and then update
        java.util.List<Pair<byte[], byte[]>> u4 = new ArrayList<>();
        ByteArrayWrapper baw1 = new ByteArrayWrapper(storageFixture.getValue());
        ByteArrayWrapper baw2 = new ByteArrayWrapper(baw1.data());

        u4.add(new Pair(baw1.data(), storageFixture.getValue()));
        u4.add(new Pair(baw2.data(), storageFixture.getValue()));

        ByteArrayWrapper version4 = storageFixture.getVersion();
        VersionedStorageView view4 = s.getView();

        try {
            view4.update(u4, new java.util.ArrayList<>());
            fail("updating with duplicated keys shall thrown exception");
        } catch (java.lang.Exception ex) {
            assertTrue("view update with duplicate key should not be possible", ex.getMessage().contains("duplicate key"));
        }

        // we can commit anyway a new version
        view4.commit(version4);
        assertEquals("Storage must have specified version.", version4, s.lastVersionID().get());
        assertEquals("Storage must have 4 versions.", 4, s.rollbackVersions().size());


        //Try to update with latest version
        VersionedStorageView view5 = s.getView();
        view5.update(u2, new java.util.ArrayList<>());
        try {
            view5.commit(version4);
            fail("already exist version in view commit shall thrown exception");
        } catch (Exception ex) {
            assertEquals("Storage should not allow updating with an already contained version.", version4, s.lastVersionID().get());
        }

        //Try to update with a version already stored previously (not latest)
        try {
            view5.commit(version3);
            fail("already exist version in view commit shall thrown exception");
        } catch (Exception ex) {
            assertEquals("Storage should not allow updating with an already contained version.", version4, s.lastVersionID().get());
        }

        //Try to rollback to non exist version
        try {
            s.rollback(storageFixture.getVersion());
            fail("Non exist version in rollback shall thrown exception");
        } catch (Exception ex) {
            assertEquals("Storage must have specified version.", version4, s.lastVersionID().get());
        }

        //Try to remove non-exist values, it is allowed
        java.util.List<byte[]> randomValues = storageFixture.getValueList(5);

        ByteArrayWrapper nonExistValuesVersion = storageFixture.getVersion();
        VersionedStorageView view6 = s.getView();
        try {
            view6.update(new java.util.ArrayList<>(), randomValues);
        } catch (Exception ex) {
            fail("It should be possible to update by removing not-existing values: " + ex.getMessage());
        }
        view6.commit(nonExistValuesVersion);
        assertEquals("Storage must have specified version.", nonExistValuesVersion, s.lastVersionID().get());

        // do a commit after several view updates
        java.util.List<Pair<byte[], byte[]>> ua = storageFixture.getKeyValueList(4);
        java.util.List<Pair<byte[], byte[]>> ub = storageFixture.getKeyValueList(20);
        java.util.List<Pair<byte[], byte[]>> uc = storageFixture.getKeyValueList(8);

        ByteArrayWrapper version7 = storageFixture.getVersion();
        VersionedStorageView view7 = s.getView();
        view7.update(ua, new java.util.ArrayList<>());
        view7.update(ub, new java.util.ArrayList<>());
        view7.update(uc, new java.util.ArrayList<>());
        view7.commit(version7);
        assertTrue("Storage must contain same elements as sample.", storageFixture.listContainment(s.getAll(), ua));
        assertTrue("Storage must contain same elements as sample.", storageFixture.listContainment(s.getAll(), ub));
        assertTrue("Storage must contain same elements as sample.", storageFixture.listContainment(s.getAll(), uc));
    }

    @Test
    public void reopenStorage() {

        File storagePath = storageFixture.tempFile();
        VersionedRocksDbStorageAdapter s = storageFixture.getStorage(storagePath);
        assertTrue("Storage expected to be empty.", s.isEmpty());
        assertFalse("Version of empty storage shall be none", s.lastVersionID().isPresent());

        int firstUpdateSize = 3;
        ByteArrayWrapper version1 = storageFixture.getVersion();
        java.util.List<Pair<byte[], byte[]>> u1 = storageFixture.getKeyValueList(firstUpdateSize);
        Pair<byte[], byte[]> firstEntryU1 = u1.get(0);

        VersionedStorageView view1 = s.getView();
        view1.update(u1, new java.util.ArrayList<>());
        view1.commit(version1);

        assertFalse("Storage expected to be not empty.", s.isEmpty());
        assertEquals("Storage must contain 3 items.", firstUpdateSize, s.getAll().size());
        assertEquals("Storage must contain value for key - " + BytesUtils.toHexString(firstEntryU1.getKey()),
                BytesUtils.toHexString(firstEntryU1.getValue()), BytesUtils.toHexString(s.get(firstEntryU1.getKey())));

        assertTrue("Storage must contain same elements as sample.", storageFixture.listContainment(u1, s.getAll()));

        assertEquals("Storage must have specified last version.", version1, s.lastVersionID().get());
        assertEquals("Storage must have 1 versions", 1, s.rollbackVersions().size());


        s.close(); // it frees resources too

        VersionedRocksDbStorageAdapter s2 = storageFixture.getStorage(storagePath);
        assertEquals("Storage must contain 3 items.", firstUpdateSize, s2.getAll().size());
        assertEquals("Storage must contain value for key - " + BytesUtils.toHexString(firstEntryU1.getKey()),
                BytesUtils.toHexString(firstEntryU1.getValue()), BytesUtils.toHexString(s2.get(firstEntryU1.getKey())));
        assertTrue("Storage must contain same elements as sample.", storageFixture.listContainment(u1, s2.getAll()));
        assertEquals("Storage must have specified version.", version1, s2.lastVersionID().get());
        assertEquals("Storage must have 1 versions.", 1, s2.rollbackVersions().size());
        assertTrue("Storage must have specified versions", s2.rollbackVersions().containsAll(Arrays.asList(version1)));

    }

    @Test
    public void testVersionedStoragePartitions() {
        VersionedStorage s = storageFixture.getStorage();
        try {
            s.addLogicalPartition("part1");
            s.addLogicalPartition("part2");
        } catch (Exception ex) {
            fail("It should be possible to add a column family: " + ex.getMessage());
        }

        // try adding an existing partition, it is ok if already exists
        try {
            s.addLogicalPartition("part2");
        } catch (Exception ex) {
            fail("It should not throw an exception when adding an existing column family");
        }

        // add data to partitions by accessing storage view
        ByteArrayWrapper version1 = storageFixture.getVersion();
        java.util.List<Pair<byte[], byte[]>> u1_1 = storageFixture.getKeyValueList(10);
        java.util.List<Pair<byte[], byte[]>> u1_2 = storageFixture.getKeyValueList(20);

        VersionedStorageView view1 = s.getView();
        view1.update("part1", u1_1, new java.util.ArrayList<>());
        view1.update("part2", u1_2, new java.util.ArrayList<>());

        // verify we can not update not-existing partitions
        try {
            view1.update("part123", u1_2, new java.util.ArrayList<>());
            fail("It should not update on partition that does not exist");
        } catch (Exception ex) {
            assertTrue(ex.getMessage().contains("No such partition"));
        }
        view1.commit(version1);

        assertTrue("Storage partiton must contain element.",
                storageFixture.compareValues(u1_2.get(2).getValue(), s.get("part2", u1_2.get(2).getKey())));
        assertFalse("Storage partiton must not contain element.",
                storageFixture.compareValues(u1_2.get(2).getValue(), s.get("part1", u1_2.get(2).getKey())));

        assertTrue("Storage partiton must contain element.",
                storageFixture.compareValues(u1_1.get(8).getValue(), s.get("part1", u1_1.get(8).getKey())));
        assertFalse("Storage partiton must not contain element.",
                storageFixture.compareValues(u1_1.get(0).getValue(), s.get("part2", u1_1.get(2).getKey())));


        // additional usages with PartitionView interface
        java.util.List<Pair<byte[], byte[]>> u2 = storageFixture.getKeyValueList(7);
        VersionedStorageView view2 = s.getView();
        ByteArrayWrapper version2 = storageFixture.getVersion();

        // verify we can not have partition views of not existing partitions
        Optional<VersionedStoragePartitionView> partitionViewNA = view2.getPartitionView("part123");
        assertTrue(partitionViewNA.isEmpty());

        VersionedStoragePartitionView partitionView2 = view2.getPartitionView("part1").get();
        partitionView2.update(u2, new java.util.ArrayList<>());
        partitionView2.commit(version2);

        assertTrue("Storage partiton must contain element.",
                storageFixture.compareValues(u2.get(5).getValue(), s.get("part1", u2.get(5).getKey())));

        // additional usages with PartitionView interface
        java.util.List<Pair<byte[], byte[]>> u3 = storageFixture.getKeyValueList(13);
        ByteArrayWrapper version3 = storageFixture.getVersion();

        Optional<VersionedStoragePartitionView> partitionViewNA2 = s.getPartitionView("part123");
        assertTrue(partitionViewNA2.isEmpty());

        VersionedStoragePartitionView partitionView3 = s.getPartitionView("part2").get();
        partitionView3.update(u3, new java.util.ArrayList<>());

        assertFalse("Storage partiton view must contain element set in a previous version.",
                storageFixture.compareValues(u2.get(4).getValue(), partitionView3.get(u2.get(4).getKey())));

        partitionView3.commit(version3);

        assertTrue("Storage partiton must contain element just set.",
                storageFixture.compareValues(u3.get(6).getValue(), s.get("part2", u3.get(6).getKey())));

        assertFalse("Storage partiton must not contain element set on different partition.",
                storageFixture.compareValues(u3.get(6).getValue(), s.get("part1", u3.get(6).getKey())));

        // try accessing a non existing partition of the storage
        Optional<VersionedStoragePartitionView> partitionViewNA3 = s.getPartitionView("part123");
        assertTrue(partitionViewNA3.isEmpty());

        // get two different view of the same partition and do separate update/commit with different k/v list
        VersionedStoragePartitionView partitionView_11 = s.getPartitionView("part1").get();
        VersionedStoragePartitionView partitionView_12 = s.getPartitionView("part1").get();

        java.util.List<Pair<byte[], byte[]>> u4_1 = storageFixture.getKeyValueList(13);
        java.util.List<Pair<byte[], byte[]>> u4_2 = storageFixture.getKeyValueList(13);
        ByteArrayWrapper version4_1 = storageFixture.getVersion();
        ByteArrayWrapper version4_2 = storageFixture.getVersion();

        partitionView_11.update(u4_1, new java.util.ArrayList<>());
        partitionView_12.update(u4_2, new java.util.ArrayList<>());

        assertTrue("Storage partiton view must contain element just set.",
                storageFixture.compareValues(u4_1.get(6).getValue(), partitionView_11.get(u4_1.get(6).getKey())));

        assertFalse("Storage partiton view must not contain element set on different view.",
                storageFixture.compareValues(u4_1.get(6).getValue(), partitionView_12.get(u4_1.get(6).getKey())));

        partitionView_11.commit(version4_1);

        assertEquals("Storage must have 4 versions.", 4, s.rollbackVersions().size());

        assertTrue("Storage partiton must contain element just committed.",
                storageFixture.compareValues(u4_1.get(6).getValue(), s.get("part1", u4_1.get(6).getKey())));

        assertFalse("Storage partiton must not contain element set on different view.",
                storageFixture.compareValues(u4_2.get(6).getValue(), s.get(u4_2.get(6).getKey())));

        int sz11 = s.getAll("part1").size();
        System.out.println("Size11 = " + sz11);
        partitionView_12.commit(version4_2);

        assertTrue("Storage partiton must contain element committed previously.",
                storageFixture.compareValues(u4_1.get(6).getValue(), s.get("part1", u4_1.get(6).getKey())));

        assertTrue("Storage partiton must contain element just committed.",
                storageFixture.compareValues(u4_2.get(6).getValue(), s.get("part1", u4_2.get(6).getKey())));

        assertEquals("Storage must have 5 versions.", 5, s.rollbackVersions().size());

        int sz12 = s.getAll("part1").size();
        System.out.println("Size12 = " + sz12);

        // get two different view of the same partition and do separate update/commit with with 1 entry with same k and different values
        VersionedStoragePartitionView partitionView_21 = s.getPartitionView("part1").get();
        VersionedStoragePartitionView partitionView_22 = s.getPartitionView("part1").get();

        java.util.List<Pair<byte[], byte[]>> u5_1 =  new java.util.ArrayList<>();
        java.util.List<Pair<byte[], byte[]>> u5_2 =  new java.util.ArrayList<>();
        u5_1.add(storageFixture.getKeyValue());
        byte[] key5_12 = u5_1.get(0).getKey();
        byte[] value5_1 = u5_1.get(0).getValue();

        u5_2.add(new Pair<>(key5_12, storageFixture.getValue()));
        byte[] value5_2 = u5_2.get(0).getValue();

        assertFalse(storageFixture.compareValues(value5_1, value5_2));

        ByteArrayWrapper version5_1 = storageFixture.getVersion();
        ByteArrayWrapper version5_2 = storageFixture.getVersion();

        partitionView_21.update(u5_1, new java.util.ArrayList<>());

        try {
            partitionView_22.update(u5_2, new java.util.ArrayList<>());
            fail("It should not update the same key on different views of the same partition");
        } catch (Exception ex) {
            assertTrue(ex.getMessage().contains("Timeout waiting to lock key"));
        }

        partitionView_21.commit(version5_1);

        assertTrue("Storage partition has same value for the key just committed.",
                storageFixture.compareValues(s.get("part1", key5_12), value5_1));

        partitionView_22.update(u5_2, new java.util.ArrayList<>());
        partitionView_22.commit(version5_2);

        assertTrue("Storage partition has same value for the key just committed.",
                storageFixture.compareValues(s.get("part1", key5_12), value5_2));



    }
}
