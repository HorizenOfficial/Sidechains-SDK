package com.horizen.customtypes;

import com.google.common.primitives.Longs;
import com.horizen.box.BoxUnlocker;
import com.horizen.box.RegularBox;
import com.horizen.proposition.PublicKey25519Proposition;
import com.horizen.transaction.SidechainTransaction;
import com.horizen.transaction.TransactionJsonSerializer;
import com.horizen.transaction.TransactionSerializer;
import com.horizen.utils.BytesUtils;
import scala.util.Failure;
import scala.util.Success;
import scala.util.Try;

import java.util.ArrayList;
import java.util.List;

public final class SemanticallyInvalidTransaction extends SidechainTransaction<PublicKey25519Proposition, RegularBox> {
    private long _timestamp;

    public SemanticallyInvalidTransaction(long timestamp) {
        _timestamp = timestamp;
    }

    @Override
    public TransactionJsonSerializer jsonSerializer() {
        return null;
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

    public static Try<SemanticallyInvalidTransaction> parseBytes(byte[] bytes) {
        try {
            return new Success<>(new SemanticallyInvalidTransaction(BytesUtils.getLong(bytes, 0)));
        } catch (Exception e) {
            return new Failure<>(e);
        }
    }
    @Override
    public byte transactionTypeId() {
        return 11;
    }
}
