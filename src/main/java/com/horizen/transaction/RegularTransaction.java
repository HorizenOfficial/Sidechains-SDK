package com.horizen.transaction;

import com.horizen.box.BoxUnlocker;
import com.horizen.box.RegularBox;
import com.horizen.box.RegularBoxSerializer;
import com.horizen.proposition.PublicKey25519Proposition;
import scala.util.Try;
import scorex.core.serialization.Serializer;

import java.util.ArrayList;
import java.util.HashMap;

public final class RegularTransaction extends NoncedBoxTransaction<PublicKey25519Proposition, RegularBox>
{

    @Override
    public RegularTransactionSerializer serializer() {
        return new RegularTransactionSerializer();
    }

    @Override
    public ArrayList<BoxUnlocker<PublicKey25519Proposition>> unlockers() {
        return null;
    }

    @Override
    public ArrayList<RegularBox> newBoxes() {
        return null;
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
        return 1;// scorex.core.ModifierTypeId @@ 1.toByte();
    }
}
