package com.horizen.transaction;

import com.google.common.primitives.Bytes;
import com.google.common.primitives.Longs;
import com.horizen.box.Box;
import com.horizen.box.BoxUnlocker;
import com.horizen.proposition.Proposition;

import java.io.ByteArrayOutputStream;
import java.util.List;


public abstract class BoxTransaction<P extends Proposition, B extends Box<P>> extends Transaction
    implements MemoryPoolCompatibilityChecker
{
    public abstract List<BoxUnlocker<P>> unlockers();

    public abstract List<B> newBoxes();

    public abstract long fee();

    public abstract long timestamp();

    @Override
    public boolean isMemoryPoolCompatible() {
        return true;
    }

    public TransactionIncompatibilityChecker incompatibilityChecker() {
        return new DefaultTransactionIncompatibilityChecker();
    }

    @Override
    public byte[] messageToSign() {
        ByteArrayOutputStream unlockersStream = new ByteArrayOutputStream();
        for(BoxUnlocker<P> u : unlockers()) {
            byte[] boxId = u.closedBoxId();
            unlockersStream.write(boxId, 0, boxId.length);
        }

        ByteArrayOutputStream newBoxesStream = new ByteArrayOutputStream();
        for(B box : newBoxes()) {
            byte[] boxBytes = box.bytes();
            newBoxesStream.write(boxBytes, 0, boxBytes.length);
        }

        return Bytes.concat(unlockersStream.toByteArray(), newBoxesStream.toByteArray(), Longs.toByteArray(timestamp()), Longs.toByteArray(fee()));
    }
}
