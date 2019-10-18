package com.horizen.customtypes;

import com.google.common.primitives.Longs;
import com.horizen.box.BoxUnlocker;
import com.horizen.box.RegularBox;
import com.horizen.proposition.PublicKey25519Proposition;
import com.horizen.transaction.SidechainTransaction;
import com.horizen.transaction.TransactionSerializer;
import com.horizen.utils.BytesUtils;
import scala.util.Failure;
import scala.util.Success;
import scala.util.Try;

import java.util.ArrayList;
import java.util.List;

public final class SemanticallyInvalidTransaction extends SidechainTransaction<PublicKey25519Proposition, RegularBox> {
    private long _timestamp;

    public static final byte TRANSACTION_TYPE_ID = 11;
    public SemanticallyInvalidTransaction(long timestamp) {
        _timestamp = timestamp;
    }

    @Override
    public boolean transactionSemanticValidity() {
        return false;
    }

    @Override
    public List<BoxUnlocker<PublicKey25519Proposition>> unlockers() {
        return new ArrayList<>();
    }

    @Override
    public List<RegularBox> newBoxes() {
        return new ArrayList<>();
    }

    @Override
    public long fee() {
        return 0;
    }

    @Override
    public long timestamp() {
        return _timestamp;
    }

    @Override
    public byte[] bytes() {
        return Longs.toByteArray(_timestamp);
    }

    @Override
    public TransactionSerializer serializer() {
        return SemanticallyInvalidTransactionSerializer.getSerializer();
    }

    public static SemanticallyInvalidTransaction parseBytes(byte[] bytes) {
        return new SemanticallyInvalidTransaction(BytesUtils.getLong(bytes, 0));
    }

    @Override
    public byte transactionTypeId() {
        return TRANSACTION_TYPE_ID;
    }

}
