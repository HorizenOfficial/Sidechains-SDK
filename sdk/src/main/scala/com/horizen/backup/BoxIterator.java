package com.horizen.backup;

import com.horizen.box.Box;
import com.horizen.box.CoinsBox;
import com.horizen.companion.SidechainBoxesCompanion;
import com.horizen.proposition.Proposition;
import com.horizen.storage.StorageIterator;
import com.horizen.utils.Utils;
import scala.util.Try;
import scorex.util.serialization.VLQByteBufferReader;

import java.nio.ByteBuffer;
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

    private boolean verifyBox(byte[] recordId, byte[] boxId) {
        return Arrays.equals(recordId, Utils.calculateKey(boxId).data());
    }

}
