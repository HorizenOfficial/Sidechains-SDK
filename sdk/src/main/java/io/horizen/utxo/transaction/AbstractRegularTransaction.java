package io.horizen.utxo.transaction;

import io.horizen.utxo.box.*;
import io.horizen.utxo.box.data.*;
import io.horizen.proof.Proof;
import io.horizen.proof.Signature25519;
import io.horizen.proof.Signature25519Serializer;
import io.horizen.proposition.Proposition;
import io.horizen.transaction.exception.TransactionSemanticValidityException;
import io.horizen.utils.ListSerializer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

// AbstractRegularTransaction is an abstract class that was designed to work with ZenBoxes only.
// This class can spent ZenBoxes and create new ZenBoxes.
// It also support fee payment logic.
public abstract class AbstractRegularTransaction
        extends SidechainNoncedTransaction<Proposition, Box<Proposition>, BoxData<Proposition, Box<Proposition>>> {

    protected final List<byte[]> inputZenBoxIds;
    protected final List<Signature25519> inputZenBoxProofs;
    protected final List<ZenBoxData> outputZenBoxesData;

    protected final long fee;

    List<BoxData<Proposition, Box<Proposition>>> allBoxesData;

    protected static ListSerializer<Signature25519> zenBoxProofsSerializer =
            new ListSerializer<>(Signature25519Serializer.getSerializer(), MAX_TRANSACTION_UNLOCKERS);
    protected static ListSerializer<ZenBoxData> zenBoxDataListSerializer =
            new ListSerializer<>(ZenBoxDataSerializer.getSerializer(), MAX_TRANSACTION_NEW_BOXES);

    public AbstractRegularTransaction(List<byte[]> inputZenBoxIds,              // zen box ids to spent
                                      List<Signature25519> inputZenBoxProofs,   // proofs to spent zen boxes
                                      List<ZenBoxData> outputZenBoxesData,      // destinations where to send zen coins
                                      long fee) {                               // fee to be paid
        this.inputZenBoxIds = inputZenBoxIds;
        this.inputZenBoxProofs = inputZenBoxProofs;
        this.outputZenBoxesData = outputZenBoxesData;
        this.fee = fee;
    }

    abstract protected List<BoxData<Proposition, Box<Proposition>>> getCustomOutputData();

    @Override
    final protected List<BoxData<Proposition, Box<Proposition>>> getOutputData() {
        if(allBoxesData == null){
            allBoxesData = new ArrayList<>();
            // Add own zen boxes data
            for(ZenBoxData zenBoxData : outputZenBoxesData){
                allBoxesData.add((BoxData) zenBoxData);
            }
            // Add custom boxes data from inheritors
            allBoxesData.addAll(getCustomOutputData());
        }
        return Collections.unmodifiableList(allBoxesData);
    }

    // Box ids to open and proofs is expected to be aggregated together and represented as Unlockers.
    // Important: all boxes which must be opened as a part of the Transaction MUST be represented as Unlocker.
    @Override
    public List<BoxUnlocker<Proposition>> unlockers() {
        // All the transactions expected to be immutable, so we keep this list cached to avoid redundant calculations.
        List<BoxUnlocker<Proposition>> unlockers = new ArrayList<>();
        // Fill the list with the zen box inputs.
        for (int i = 0; i < inputZenBoxIds.size() && i < inputZenBoxProofs.size(); i++) {
            int finalI = i;
            BoxUnlocker<Proposition> unlocker = new BoxUnlocker<Proposition>() {
                @Override
                public byte[] closedBoxId() {
                    return inputZenBoxIds.get(finalI);
                }

                @Override
                public Proof boxKey() {
                    return inputZenBoxProofs.get(finalI);
                }
            };
            unlockers.add(unlocker);
        }

        return unlockers;
    }

    @Override
    public long fee() {
        return fee;
    }

    @Override
    public void transactionSemanticValidity() throws TransactionSemanticValidityException {
        // check that we have enough proofs.
        if(inputZenBoxIds.size() != inputZenBoxProofs.size()) {
            throw new TransactionSemanticValidityException(String.format("Transaction [%s] is semantically invalid: " +
                    "inputs number is not consistent to proofs number.", id()));
        }

        // Check that we have at least one input
        if(inputZenBoxIds.isEmpty()) {
            throw new TransactionSemanticValidityException(String.format("Transaction [%s] is semantically invalid: " +
                    "no zen inputs found.", id()));
        }
    }
}
