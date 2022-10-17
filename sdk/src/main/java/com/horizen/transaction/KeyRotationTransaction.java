package com.horizen.transaction;

import com.google.common.primitives.Ints;
import com.horizen.box.Box;
import com.horizen.box.data.BoxData;
import com.horizen.box.data.ZenBoxData;
import com.horizen.certificatesubmitter.keys.KeyRotationProof;
import com.horizen.proof.Signature25519;
import com.horizen.proposition.Proposition;
import scala.Array;
import sparkz.core.serialization.BytesSerializable;
import sparkz.core.serialization.SparkzSerializer;

import java.util.ArrayList;
import java.util.List;

import static com.horizen.transaction.CoreTransactionsIdsEnum.KeyRotationTransactionId;

/*
 * KeyRotationTransaction is used for key rotation of signer or master key of certificate submitter
 */
public class KeyRotationTransaction extends AbstractRegularTransaction
{
    public final static byte KEY_ROTATION_TRANSACTION_VERSION = 1;
    private final KeyRotationProof keyRotationProof;

    public KeyRotationTransaction(List<byte[]> inputZenBoxIds, List<Signature25519> inputZenBoxProofs, List<ZenBoxData> outputZenBoxesData, long fee, KeyRotationProof keyRotationProof) {
        super(inputZenBoxIds, inputZenBoxProofs, outputZenBoxesData, fee);

        this.keyRotationProof = keyRotationProof;
    }

    @Override
    public SparkzSerializer<BytesSerializable> serializer() {
        return keyRotationProof.serializer();
    }

    @Override
    protected List<BoxData<Proposition, Box<Proposition>>> getCustomOutputData() {
        return new ArrayList<>();
    }

    @Override
    public byte transactionTypeId() {
        return KeyRotationTransactionId.id();
    }

    @Override
    public byte version() {
        return KEY_ROTATION_TRANSACTION_VERSION;
    }

    @Override
    public byte[] customFieldsData() {
        return Array.emptyByteArray();
    }

    @Override
    public byte[] customDataMessageToSign() {
        return Ints.toByteArray(this.keyRotationProof.index());
    }
}