package com.horizen.transaction;
/*
import com.horizen.box.NoncedBox;
import com.horizen.box.BoxUnlocker;
import com.horizen.proposition.Proposition;
import scorex.core.utils.ScorexEncoder;

import java.util.ArrayList;
import java.util.List;

public final class CertifierUnlockRequestTransaction
    extends SidechainTransaction<Proposition, NoncedBox<Proposition>>
{

    public static final byte TRANSACTION_TYPE_ID = 5;

    @Override
    public CertifierUnlockRequestTransactionSerializer serializer() {
        return new CertifierUnlockRequestTransactionSerializer();
    }

    @Override
    public List<BoxUnlocker<Proposition>> unlockers() { return null; }

    // nothing to create
    @Override
    public List<NoncedBox<Proposition>> newBoxes() {
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
    public boolean transactionSemanticValidity() {
        return false;
    }

    @Override
    public byte transactionTypeId() {
        return TRANSACTION_TYPE_ID; // scorex.core.ModifierTypeId @@ 5.toByte
    }

    @Override
    public byte[] bytes() {
        return null;
    }


}
*/