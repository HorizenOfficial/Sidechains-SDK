package com.horizen.transaction;

import com.horizen.box.BoxUnlocker;
import com.horizen.proposition.PublicKey25519Proposition;
import com.horizen.box.ZenBox;
import com.horizen.transaction.exception.TransactionSemanticValidityException;
import scala.Array;
import scorex.core.serialization.ScorexSerializer;

import java.util.Collections;
import java.util.List;

import static com.horizen.transaction.CoreTransactionsIdsEnum.FeePaymentsTransactionId;
import static java.util.Collections.emptyList;

/*
 * FeePaymentsTransaction is used for transaction-like representation of the forger fee payments
 * It is not supposed to be put into the mempool or being included into the SidechainBlock.
 */
public class FeePaymentsTransaction extends BoxTransaction<PublicKey25519Proposition, ZenBox>
{
    public final static byte FEE_PAYMENTS_TRANSACTION_VERSION = 1;

    private final List<ZenBox> feePayments;
    private final byte version;

    public FeePaymentsTransaction(List<ZenBox> feePayments, byte version) {
        this.feePayments = feePayments;
        this.version = version;
    }

    @Override
    public void semanticValidity() throws TransactionSemanticValidityException {
        if (version != FEE_PAYMENTS_TRANSACTION_VERSION) {
            throw new TransactionSemanticValidityException(String.format("Transaction [%s] is semantically invalid: " +
                    "unsupported version number.", id()));
        }

        if (feePayments.isEmpty())
            throw new TransactionSemanticValidityException(String.format("Transaction [%s] is semantically invalid: " +
                    "no output data present.", id()));
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
        return emptyList();
    }

    @Override
    public TransactionIncompatibilityChecker incompatibilityChecker() {
        return MempoolIncompatibleTransactionIncompatibilityChecker.getChecker();
    }

    @Override
    public List<ZenBox> newBoxes() {
        return Collections.unmodifiableList(feePayments);
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
    public Boolean isCustom() { return false; }

    @Override
    public ScorexSerializer serializer() {
        return FeePaymentsTransactionSerializer.getSerializer();
    }
}