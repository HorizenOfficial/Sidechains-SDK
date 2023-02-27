package com.horizen.utxo.backup;

import com.horizen.utxo.backup.BackupBox;
import com.horizen.utxo.backup.BoxIterator;
import com.horizen.utxo.box.Box;
import com.horizen.utxo.box.BoxSerializer;
import com.horizen.utxo.box.ZenBox;
import com.horizen.companion.SidechainBoxesCompanion;
import com.horizen.customtypes.CustomBox;
import com.horizen.customtypes.CustomBoxSerializer;
import com.horizen.fixtures.BoxFixtureClass;
import com.horizen.proposition.Proposition;
import com.horizen.utxo.storage.BackupStorage;
import com.horizen.storage.leveldb.VersionedLevelDbStorageAdapter;
import com.horizen.utils.ByteArrayWrapper;
import com.horizen.utils.Pair;
import com.horizen.utils.Utils;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import static org.junit.Assert.fail;

public class BoxIteratorTest extends BoxFixtureClass {
    @Rule
    public TemporaryFolder temporaryFolder= new TemporaryFolder();


    SidechainBoxesCompanion sidechainBoxesCompanion;
    HashMap<Byte, BoxSerializer<Box<Proposition>>> customBoxSerializers = new HashMap<>();
    List<CustomBox> customBoxes;
    List<ZenBox> zenBoxes;
    List<Pair<ByteArrayWrapper, ByteArrayWrapper>> customBoxToSave = new ArrayList<>();
    List<Pair<ByteArrayWrapper, ByteArrayWrapper>> zenBoxToSave = new ArrayList<>();
    int nBoxes = 5;

    @Before
    public void setup() {
        customBoxSerializers.put(CustomBox.BOX_TYPE_ID, (BoxSerializer) CustomBoxSerializer.getSerializer());
        sidechainBoxesCompanion = new SidechainBoxesCompanion(customBoxSerializers);

        customBoxes = getCustomBoxList(nBoxes);
        for (CustomBox box : customBoxes) {
            ByteArrayWrapper key = Utils.calculateKey(box.id());
            ByteArrayWrapper value = new ByteArrayWrapper(sidechainBoxesCompanion.toBytes((Box) box));
            customBoxToSave.add(new Pair<>(key, value));
        }
        zenBoxes = getZenBoxList(nBoxes);
        for (ZenBox box : zenBoxes) {
            ByteArrayWrapper key = Utils.calculateKey(box.id());
            ByteArrayWrapper value = new ByteArrayWrapper(sidechainBoxesCompanion.toBytes((Box) box));
            zenBoxToSave.add(new Pair<>(key, value));
        }

    }

    @Test
    public void BoxIteratorTestCustomBoxes() throws IOException {
        //Create temporary BackupStorage
        File stateStorageFile = temporaryFolder.newFolder("stateStorage");
        BackupStorage backupStorage = new BackupStorage(new VersionedLevelDbStorageAdapter(stateStorageFile), sidechainBoxesCompanion);

        //Add an additional random element to customBoxToSave list.
        customBoxToSave.add(new Pair(new ByteArrayWrapper("key1".getBytes(StandardCharsets.UTF_8)), new ByteArrayWrapper("value1".getBytes(StandardCharsets.UTF_8))));

        //Populate the BackupStorage
        backupStorage.update(new ByteArrayWrapper(Utils.nextVersion()), customBoxToSave).get();

        //Create a BoxIterator
        BoxIterator boxIterator = backupStorage.getBoxIterator();

        //Read the storage using the BoxIterator
        ArrayList<BackupBox> foundBoxes = readStorage(boxIterator);

        //Test that we read the correct amount of Boxes (nBoxes) and we ignore non-boxes elements.
        assert(foundBoxes.size() == nBoxes);

        //Test the content of the boxes.
        for (BackupBox backupBox : foundBoxes) {
            ByteArrayWrapper newKey = new ByteArrayWrapper(backupBox.getBoxKey());
            ByteArrayWrapper newValue = new ByteArrayWrapper(backupBox.getBoxValue());
            assert(customBoxToSave.contains(new Pair(newKey, newValue)));
            assert(backupBox.getBoxTypeId() == CustomBox.BOX_TYPE_ID);
            assert(customBoxes.contains(backupBox.getBox()));
        }

        //Test that the iterator is empty
        Optional<BackupBox> emptyBox = boxIterator.nextBox();
        assert(emptyBox.isEmpty());

        //Test the seekToFirst method
        boxIterator.seekToFirst();
        foundBoxes = readStorage(boxIterator);
        assert(foundBoxes.size() == nBoxes);
    }

    @Test
    public void BoxIteratorTestCoinBoxes() throws IOException {
        //Create temporary BackupStorage
        File stateStorageFile = temporaryFolder.newFolder("stateStorage");
        BackupStorage backupStorage = new BackupStorage(new VersionedLevelDbStorageAdapter(stateStorageFile), sidechainBoxesCompanion);

        //Populate the BackupStorage
        backupStorage.update(new ByteArrayWrapper(Utils.nextVersion()), zenBoxToSave).get();

        //Create a BoxIterator
        BoxIterator boxIterator = backupStorage.getBoxIterator();

        //Read the storage using the BoxIterator
        try {
            boxIterator.nextBox();
            fail("We should not be able to retrieve Coin Boxes!");
        } catch (RuntimeException e) {
            System.out.println(e.getMessage());
            assert(e.getMessage().equals("Coin boxes are not eligible to be restored!"));
        }

    }

    @Test
    public void BoxIteratorNextBoxesTest() throws IOException {
        //Create temporary BackupStorage
        File stateStorageFile = temporaryFolder.newFolder("stateStorage");
        BackupStorage backupStorage = new BackupStorage(new VersionedLevelDbStorageAdapter(stateStorageFile), sidechainBoxesCompanion);

        //Add an additional random element to customBoxToSave list.
        customBoxToSave.add(new Pair(new ByteArrayWrapper("key1".getBytes(StandardCharsets.UTF_8)), new ByteArrayWrapper("value1".getBytes(StandardCharsets.UTF_8))));

        //Populate the BackupStorage
        backupStorage.update(new ByteArrayWrapper(Utils.nextVersion()), customBoxToSave).get();

        //Create a BoxIterator
        BoxIterator boxIterator = backupStorage.getBoxIterator();

        //Test nextBoxes with nElement = 0 and empty key to seek
        List<Box<Proposition>> foundBoxes = boxIterator.getNextBoxes(0,Optional.empty());
        assert(foundBoxes.isEmpty());

        //Test nextBoxes with nElement < 0 and empty key to seek
        foundBoxes = boxIterator.getNextBoxes(-1,Optional.empty());
        boxIterator.seekToFirst();
        assert(foundBoxes.isEmpty());

        //Test nextBoxes with nElement = nBoxes and empty key to seek
        boxIterator.seekToFirst();
        foundBoxes = boxIterator.getNextBoxes(nBoxes,Optional.empty());
        assert(foundBoxes.size() == nBoxes);
        //Test the content of the boxes.
        for (Box<Proposition> box : foundBoxes) {
            ByteArrayWrapper newKey = Utils.calculateKey(box.id());
            ByteArrayWrapper newValue = new ByteArrayWrapper(sidechainBoxesCompanion.toBytes((Box) box));
            assert(customBoxToSave.contains(new Pair(newKey, newValue)));
        }

        //Test nextBoxes with nElement > nBoxes and empty key to seek
        boxIterator.seekToFirst();
        foundBoxes = boxIterator.getNextBoxes(nBoxes+10,Optional.empty());
        assert(foundBoxes.size() == nBoxes);
        //Test the content of the boxes.
        for (Box<Proposition> box : foundBoxes) {
            ByteArrayWrapper newKey = Utils.calculateKey(box.id());
            ByteArrayWrapper newValue = new ByteArrayWrapper(sidechainBoxesCompanion.toBytes((Box) box));
            assert(customBoxToSave.contains(new Pair(newKey, newValue)));
        }

        //Test nextBoxes with nElement = nBoxes and keyToSeek=1st box saved
        boxIterator.seekToFirst();
        List<Box<Proposition>> boxStoredOrder = foundBoxes;
        foundBoxes = boxIterator.getNextBoxes(nBoxes,Optional.of(boxStoredOrder.get(0).id()));
        assert(foundBoxes.size() == nBoxes-1);
        assert(!foundBoxes.contains(boxStoredOrder.get(0)));
        //Test the content of the boxes.
        for (Box<Proposition> box : foundBoxes) {
            ByteArrayWrapper newKey = Utils.calculateKey(box.id());
            ByteArrayWrapper newValue = new ByteArrayWrapper(sidechainBoxesCompanion.toBytes((Box) box));
            assert(customBoxToSave.contains(new Pair(newKey, newValue)));
        }

        //Test nextBoxes with nElement = nBoxes and keyToSeek=last box saved
        boxIterator.seekToFirst();
        foundBoxes = boxIterator.getNextBoxes(nBoxes,Optional.of(boxStoredOrder.get(boxStoredOrder.size()-1).id()));
        assert(foundBoxes.size() == 0);

    }


    private ArrayList<BackupBox> readStorage(BoxIterator boxIterator) {
        ArrayList<BackupBox> storedBoxes = new ArrayList<>();

        Optional<BackupBox> optionalBox = boxIterator.nextBox();
        while(optionalBox.isPresent()) {
            storedBoxes.add(optionalBox.get());
            optionalBox = boxIterator.nextBox();
        }
        return storedBoxes;
    }
}
