package com.horizen.transaction;

import com.horizen.box.*;
import com.horizen.box.data.*;
import com.horizen.proof.Proof;
import com.horizen.proposition.Proposition;
import com.horizen.transaction.exception.TransactionSemanticValidityException;
import scala.Array;

import static com.horizen.transaction.CoreTransactionsIdsEnum.SidechainCoreTransactionId;

import java.util.*;


public final class SidechainCoreTransaction
        extends SidechainNoncedTransaction<Proposition, Box<Proposition>, BoxData<Proposition, Box<Proposition>>>
{
    public final static byte SIDECHAIN_CORE_TRANSACTION_VERSION = 1;

    final List<byte[]> inputsIds;
    private final List<BoxData<Proposition, Box<Proposition>>> outputsData;
    final List<Proof<Proposition>> proofs;

    private final long fee;

    private final byte version;

    private List<BoxUnlocker<Proposition>> unlockers;


    public SidechainCoreTransaction(List<byte[]> inputsIds,
                             List<BoxData<Proposition, Box<Proposition>>> outputsData,
                             List<Proof<Proposition>> proofs,
                             long fee,
                             byte version) {
        Objects.requireNonNull(inputsIds, "Inputs Ids list can't be null.");
        Objects.requireNonNull(outputsData, "Outputs Data list can't be null.");
        Objects.requireNonNull(proofs, "Proofs list can't be null.");
        // Do we need to care about inputs ids length here or state/serialization check is enough?

        this.inputsIds = inputsIds;
        this.outputsData = outputsData;
        this.proofs = proofs;
        this.fee = fee;
        this.version = version;
    }

    @Override
    public TransactionSerializer serializer() {
        return SidechainCoreTransactionSerializer.getSerializer();
    }

    @Override
    public synchronized List<BoxUnlocker<Proposition>> unlockers() {
        if(unlockers == null) {
            unlockers = new ArrayList<>();
            for (int i = 0; i < inputsIds.size() && i < proofs.size(); i++) {
                int finalI = i;
                BoxUnlocker<Proposition> unlocker = new BoxUnlocker<Proposition>() {
                    @Override
                    public byte[] closedBoxId() {
                        return inputsIds.get(finalI);
                    }

                    @Override
                    public Proof boxKey() {
                        return proofs.get(finalI);
                    }
                };
                unlockers.add(unlocker);
            }
        }

        return Collections.unmodifiableList(unlockers);
    }

    @Override
    protected List<BoxData<Proposition, Box<Proposition>>> getOutputData(){
        return outputsData;
    }

    @Override
    public long fee() {
        return fee;
    }

    @Override
    public void transactionSemanticValidity() throws TransactionSemanticValidityException {
        if (version != SIDECHAIN_CORE_TRANSACTION_VERSION) {
            throw new TransactionSemanticValidityException(String.format("Transaction [%s] is semantically invalid: " +
                    "unsupported version number.", id()));
        }

        if (inputsIds.isEmpty() || outputsData.isEmpty())
            throw new TransactionSemanticValidityException(String.format("Transaction [%s] is semantically invalid: " +
                    "no input and output data present.", id()));

        // check that we have enough proofs and try to open each box only once.
        if (inputsIds.size() != proofs.size() || inputsIds.size() != boxIdsToOpen().size())
            throw new TransactionSemanticValidityException(String.format("Transaction [%s] is semantically invalid: " +
                    "inputs number is not consistent to proofs number.", id()));
    }

    @Override
    public byte transactionTypeId() {
        return SidechainCoreTransactionId.id();
    }

    @Override
    public byte version() {
        return version;
    }

    @Override
    public Boolean isCustom() { return false; }

    @Override
    public byte[] customFieldsData() {
        return Array.emptyByteArray();
    }

    @Override
    public byte[] customDataMessageToSign() {
        return Array.emptyByteArray();
    }
}
