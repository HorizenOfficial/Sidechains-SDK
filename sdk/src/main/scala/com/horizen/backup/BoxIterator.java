package com.horizen.backup;

import com.horizen.box.Box;
import com.horizen.box.CoinsBox;
import com.horizen.companion.SidechainBoxesCompanion;
import com.horizen.proposition.Proposition;
import com.horizen.storage.StorageIterator;
import com.horizen.utils.Utils;
import scala.util.Try;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;

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

    public Optional<BackupBox> nextBox() throws RuntimeException {
        while (iterator.hasNext()) {
            Map.Entry<byte[], byte[]> entry = iterator.next();
            Try<Box<Proposition>> box = sidechainBoxesCompanion.parseBytesTry(entry.getValue());
            if (box.isSuccess()) {
                Box<Proposition> currBox = box.get();
                if (verifyBox(entry.getKey(), currBox.id())) {
                    if (!(currBox instanceof CoinsBox)) {
                        return Optional.of(new BackupBox(currBox, entry.getKey(), entry.getValue()));
                    }
                    else {
                        throw new RuntimeException("Coin boxes are not eligible to be restored!");
                    }
                } else {
                    throw new RuntimeException("Unable to reconstruct the same box id to restore!");
                }
            }
        }
        return Optional.empty();
    }

    private boolean verifyBox(byte[] recordId, byte[] boxId) {
        return Arrays.equals(recordId, Utils.calculateKey(boxId).data());
    }

}
