package io.horizen.utxo.backup;

import io.horizen.proposition.Proposition;
import io.horizen.storage.StorageIterator;
import io.horizen.utils.Utils;
import io.horizen.utxo.box.Box;
import io.horizen.utxo.box.CoinsBox;
import io.horizen.utxo.companion.SidechainBoxesCompanion;
import scala.util.Try;
import sparkz.util.serialization.VLQByteBufferReader;

import java.nio.ByteBuffer;
import java.util.*;

public class BoxIterator {
    private final StorageIterator iterator;
    private final SidechainBoxesCompanion sidechainBoxesCompanion;

    public BoxIterator(StorageIterator iterator, SidechainBoxesCompanion sidechainBoxesCompanion) {
        this.iterator = iterator;
        this.sidechainBoxesCompanion = sidechainBoxesCompanion;
        this.iterator.seekToFirst();
    }

    public void seekToFirst() {
        this.iterator.seekToFirst();
    }

    public void seekIterator(byte[] key) {
        iterator.seek(key);
    }

    public Optional<BackupBox> nextBox(boolean ignoreCoinBox) throws RuntimeException {
        while (iterator.hasNext()) {
            Map.Entry<byte[], byte[]> entry = iterator.next();
            VLQByteBufferReader reader = new VLQByteBufferReader(ByteBuffer.wrap(entry.getValue()));
            Try<Box<Proposition>> tryBox = sidechainBoxesCompanion.parseTry(reader);

            if (tryBox.isSuccess() && reader.remaining() == 0) {
                Box<Proposition> currBox = tryBox.get();
                if (verifyBox(entry.getKey(), currBox.id())) {
                    if (!(currBox instanceof CoinsBox)) {
                        return Optional.of(new BackupBox(currBox, entry.getKey(), entry.getValue()));
                    }
                    else {
                        if (!ignoreCoinBox)
                            throw new RuntimeException("Coin boxes are not eligible to be restored!");
                    }
                }
            }
        }
        return Optional.empty();
    }

    public Optional<BackupBox> nextBox() throws RuntimeException {
        return nextBox(false);
    }

    public List<Box<Proposition>> getNextBoxes(int nElement, Optional<byte []> keyToSeek) {
        if (keyToSeek.isPresent()) {
            this.seekIterator(Utils.calculateKey(keyToSeek.get()).data());
            this.nextBox();
        } else {
            this.seekToFirst();
        }
        List<Box<Proposition>> boxes = new ArrayList<>();
        Optional<BackupBox> nextBox = this.nextBox();
        while(boxes.size() < nElement && nextBox.isPresent()) {
            boxes.add(nextBox.get().getBox());
            nextBox = this.nextBox();
        }
        return boxes;
    }

    private boolean verifyBox(byte[] recordId, byte[] boxId) {
        return Arrays.equals(recordId, Utils.calculateKey(boxId).data());
    }

}
