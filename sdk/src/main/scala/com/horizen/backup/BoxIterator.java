package com.horizen.backup;

import com.horizen.box.Box;
import com.horizen.box.CoinsBox;
import com.horizen.companion.SidechainBoxesCompanion;
import com.horizen.proposition.Proposition;
import com.horizen.storage.StorageIterator;
import com.horizen.utils.ByteArrayWrapper;
import scala.util.Try;
import scorex.crypto.hash.Blake2b256;

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

    public Optional<BackupBox> nextBox() {
        while (iterator.hasNext()) {
            Map.Entry<byte[], byte[]> entry = iterator.next();
            Try<Box<Proposition>> box = sidechainBoxesCompanion.parseBytesTry(entry.getValue());
            if (box.isSuccess()) {
                Box<Proposition> currBox = box.get();
                if (verifyBox(entry.getKey(), currBox.id()) &&
                        (!(currBox instanceof CoinsBox))
                ) {
                    return Optional.of(new BackupBox(currBox, entry.getKey(), entry.getValue()));
                }
            }
        }
        return Optional.empty();
    }

    private boolean verifyBox(byte[] recordId, byte[] boxId) {
        return Arrays.equals(recordId, calculateKey(boxId).data());
    }

    private ByteArrayWrapper calculateKey(byte[] boxId) {
        return new ByteArrayWrapper((byte[]) Blake2b256.hash(boxId));
    }
}
