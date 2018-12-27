package com.horizen.transaction;

import com.horizen.box.NoncedBox;
import com.horizen.box.BoxUnlocker;
import com.horizen.proposition.Proposition;
import scala.util.Try;
import scorex.core.serialization.Serializer;

import java.util.ArrayList;
import java.util.HashMap;

public final class WithdrawalRequestTransaction extends NoncedBoxTransaction<Proposition, NoncedBox<Proposition>>
{
    @Override
    public WithdrawalRequestTransactionSerializer serializer() {
        return new WithdrawalRequestTransactionSerializer();
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
        return 4; // scorex.core.ModifierTypeId @@ 4.toByte
    }
}
