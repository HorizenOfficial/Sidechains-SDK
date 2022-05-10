package com.horizen.backup;

import com.horizen.box.Box;
import com.horizen.box.BoxSerializer;
import com.horizen.box.ZenBox;
import com.horizen.companion.SidechainBoxesCompanion;
import com.horizen.customtypes.CustomBox;
import com.horizen.customtypes.CustomBoxSerializer;
import com.horizen.fixtures.BoxFixtureClass;
import com.horizen.proposition.Proposition;
import com.horizen.storage.BackupStorage;
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
        customBoxToSave.add(new Pair(new ByteArrayWrapper("key1".getBytes()), new ByteArrayWrapper("value1".getBytes())));

        //Popoulate the BackupStorage
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

        //Popoulate the BackupStorage
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
