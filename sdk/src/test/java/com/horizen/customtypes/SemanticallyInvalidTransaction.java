package com.horizen.customtypes;

import com.horizen.utxo.box.BoxUnlocker;
import com.horizen.utxo.box.ZenBox;
import com.horizen.proposition.PublicKey25519Proposition;
import com.horizen.utxo.transaction.SidechainTransaction;
import com.horizen.transaction.TransactionSerializer;
import com.horizen.transaction.exception.TransactionSemanticValidityException;

import java.util.ArrayList;
import java.util.List;

public final class SemanticallyInvalidTransaction extends SidechainTransaction<PublicKey25519Proposition, ZenBox> {
    public static final byte TRANSACTION_TYPE_ID = 11;
    public SemanticallyInvalidTransaction() {}

    @Override
    public void transactionSemanticValidity() throws TransactionSemanticValidityException {
        throw new TransactionSemanticValidityException("invalid tx");
    }

    @Override
    public List<BoxUnlocker<PublicKey25519Proposition>> unlockers() {
        return new ArrayList<>();
    }

    @Override
    public List<PublicKey25519Proposition> newBoxesPropositions() { return new ArrayList<>(); }

    @Override
    public List<ZenBox> newBoxes() {
        return new ArrayList<>();
    }

    @Override
    public long fee() {
        return 0;
    }

    @Override
    public byte[] customFieldsData() {
        return new byte[0];
    }

    @Override
    public byte[] customDataMessageToSign() {
        return new byte[0];
    }

    @Override
    public TransactionSerializer serializer() {
        return SemanticallyInvalidTransactionSerializer.getSerializer();
    }

    @Override
    public byte transactionTypeId() {
        return TRANSACTION_TYPE_ID;
    }

    @Override
    public byte version() {
        return Byte.MAX_VALUE;
    }

    @Override
    public long size() { return 100;}
}
