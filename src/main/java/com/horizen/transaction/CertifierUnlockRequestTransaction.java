package com.horizen.transaction;

import com.horizen.box.NoncedBox;
import com.horizen.box.BoxUnlocker;
import com.horizen.proposition.Proposition;
import scala.util.Try;
import scorex.core.serialization.Serializer;

import java.util.ArrayList;
import java.util.HashMap;

public final class CertifierUnlockRequestTransaction extends NoncedBoxTransaction<Proposition, NoncedBox<Proposition>>
{
    @Override
    public CertifierUnlockRequestTransactionSerializer serializer() {
        return new CertifierUnlockRequestTransactionSerializer();
    }

    @Override
    public ArrayList<BoxUnlocker<Proposition>> unlockers() { return null; }

    // nothing to create
    @Override
    public ArrayList<NoncedBox<Proposition>> newBoxes() {
        return new ArrayList<NoncedBox<Proposition>>();
    }

    @Override
    public long fee() {
        return 0;
    }

    @Override
    public long timestamp() {
        return 0;
    }

    @Override
    public byte transactionTypeId() {
        return 5; // scorex.core.ModifierTypeId @@ 5.toByte
    }

    @Override
    public byte[] bytes() {
        return null;
    }
}
