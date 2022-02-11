package com.horizen.transaction;

import com.horizen.box.BoxUnlocker;
import com.horizen.proposition.PublicKey25519Proposition;
import com.horizen.box.ZenBox;
import com.horizen.box.data.ZenBoxData;
import com.horizen.transaction.exception.TransactionSemanticValidityException;
import scala.Array;
import scorex.core.serialization.ScorexSerializer;

import java.util.List;
import java.util.ArrayList;

import static com.horizen.transaction.CoreTransactionsIdsEnum.FeePaymentsTransactionId;

public class FeePaymentsTransaction extends SidechainTransaction<PublicKey25519Proposition, ZenBox>
{
    public final static byte FEE_PAYMENTS_TRANSACTION_VERSION = 1;

    private final List<ZenBoxData> outputsData;
    private final byte version;

    public FeePaymentsTransaction(List<ZenBoxData> outputsData, byte version) {
        this.outputsData = outputsData;
        this.version = version;
    }

    List<ZenBoxData> getOutputData() {
        return outputsData;
    }

    @Override
    public void transactionSemanticValidity() throws TransactionSemanticValidityException {
        if (version != FEE_PAYMENTS_TRANSACTION_VERSION) {
            throw new TransactionSemanticValidityException(String.format("Transaction [%s] is semantically invalid: " +
                    "unsupported version number.", id()));
        }

        if (outputsData.isEmpty())
            throw new TransactionSemanticValidityException(String.format("Transaction [%s] is semantically invalid: " +
                    "no output data present.", id()));
    }

    @Override
    public List<PublicKey25519Proposition> newBoxesPropositions() {
        return new ArrayList<>();
    }

    @Override
    public byte[] customDataMessageToSign() {
        return Array.emptyByteArray();
    }

    @Override
    public byte[] customFieldsData() {
        return Array.emptyByteArray();
    }

    @Override
    public List<BoxUnlocker<PublicKey25519Proposition>> unlockers() {
        return null;
    }

    // no checker exists for current transaction type
    // keep check in mempool against this
    @Override
    public TransactionIncompatibilityChecker incompatibilityChecker() {
        return null;
    }

    @Override
    public List<ZenBox> newBoxes() {
        return null;
    }

    @Override
    public long fee() {
        return 0;
    }

    @Override
    public byte transactionTypeId() {
        return FeePaymentsTransactionId.id();
    }

    @Override
    public byte version() {
        return version;
    }

    @Override
    public ScorexSerializer serializer() {
        return FeePaymentsTransactionSerializer.getSerializer();
    }
}