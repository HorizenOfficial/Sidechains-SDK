package com.horizen.transaction;

import com.horizen.box.*;
import com.horizen.box.data.*;
import com.horizen.proof.Proof;
import com.horizen.proof.Signature25519;
import com.horizen.proof.Signature25519Serializer;
import com.horizen.proposition.Proposition;
import com.horizen.transaction.exception.TransactionSemanticValidityException;
import com.horizen.utils.ListSerializer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

// AbstractRegularTransaction is an abstract class that was designed to work with ZenBoxes only.
// This class can spent ZenBoxes and create new ZenBoxes.
// It also support fee payment logic.
public abstract class AbstractRegularTransaction
        extends SidechainNoncedTransaction<Proposition, Box<Proposition>, NoncedBoxData<Proposition, Box<Proposition>>> {

    protected final List<byte[]> inputZenBoxIds;
    protected final List<Signature25519> inputZenBoxProofs;
    protected final List<ZenBoxData> outputZenBoxesData;

    protected final long fee;

    List<NoncedBoxData<Proposition, Box<Proposition>>> allBoxesData;

    protected static ListSerializer<Signature25519> zenBoxProofsSerializer =
            new ListSerializer<>(Signature25519Serializer.getSerializer(), MAX_TRANSACTION_UNLOCKERS);
    protected static ListSerializer<ZenBoxData> zenBoxDataListSerializer =
            new ListSerializer<>(ZenBoxDataSerializer.getSerializer(), MAX_TRANSACTION_NEW_BOXES);

    public AbstractRegularTransaction(List<byte[]> inputZenBoxIds,              // zen box ids to spent
                                      List<Signature25519> inputZenBoxProofs,   // proofs to spent zen boxes
                                      List<ZenBoxData> outputZenBoxesData,      // destinations where to send zen coins
                                      long fee) {                                 // fee to be paid

        // Parameters sanity check
        if(inputZenBoxIds == null || inputZenBoxProofs == null || outputZenBoxesData == null || fee < 0 ||
           inputZenBoxIds.isEmpty() || inputZenBoxProofs.isEmpty() || outputZenBoxesData.isEmpty()){
            throw new IllegalArgumentException("Some of the input parameters are unacceptable!");
        }

        // Number of input ids should be equal to number of proofs, otherwise transaction is for sure invalid.
        if(inputZenBoxIds.size() != inputZenBoxProofs.size()){
            throw new IllegalArgumentException("Zen box inputs list size is different to proving signatures list size!");
        }

        this.inputZenBoxIds = inputZenBoxIds;
        this.inputZenBoxProofs = inputZenBoxProofs;
        this.outputZenBoxesData = outputZenBoxesData;
        this.fee = fee;
    }

    abstract protected List<NoncedBoxData<Proposition, Box<Proposition>>> getCustomOutputData();

    @Override
    final protected List<NoncedBoxData<Proposition, Box<Proposition>>> getOutputData(){
        if(allBoxesData == null){
            allBoxesData = new ArrayList<>();
            // Add own zen boxes data
            for(ZenBoxData zenBoxData : outputZenBoxesData){
                allBoxesData.add((NoncedBoxData) zenBoxData);
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
    }
}
