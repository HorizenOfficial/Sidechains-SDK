package com.horizen.transaction;

import com.horizen.box.*;
import com.horizen.box.data.*;
import com.horizen.proof.Proof;
import com.horizen.proof.Signature25519;
import com.horizen.proof.Signature25519Serializer;
import com.horizen.proposition.Proposition;
import com.horizen.utils.ListSerializer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

// AbstractRegularTransaction is an abstract class that was designed to work with RegularBoxes only.
// This class can spent RegularBoxes and create new RegularBoxes.
// It also support fee payment logic.
public abstract class AbstractRegularTransaction
        extends SidechainNoncedTransaction<Proposition, NoncedBox<Proposition>, NoncedBoxData<Proposition, NoncedBox<Proposition>>> {

    protected final List<byte[]> inputRegularBoxIds;
    protected final List<Signature25519> inputRegularBoxProofs;
    protected final List<RegularBoxData> outputRegularBoxesData;

    protected final long fee;
    protected final long timestamp;

    List<NoncedBoxData<Proposition, NoncedBox<Proposition>>> allBoxesData;

    protected static ListSerializer<Signature25519> regularBoxProofsSerializer =
            new ListSerializer<>(Signature25519Serializer.getSerializer(), MAX_TRANSACTION_UNLOCKERS);
    protected static ListSerializer<RegularBoxData> regularBoxDataListSerializer =
            new ListSerializer<>(RegularBoxDataSerializer.getSerializer(), MAX_TRANSACTION_NEW_BOXES);

    public AbstractRegularTransaction(List<byte[]> inputRegularBoxIds,              // regular box ids to spent
                                      List<Signature25519> inputRegularBoxProofs,   // proofs to spent regular boxes
                                      List<RegularBoxData> outputRegularBoxesData,  // destinations where to send regular coins
                                      long fee,                                     // fee to be paid
                                      long timestamp) {                             // creation time in milliseconds from epoch

        // Parameters sanity check
        if(inputRegularBoxIds == null || inputRegularBoxProofs == null || outputRegularBoxesData == null ||
           fee < 0 || timestamp < 0 ||
           inputRegularBoxIds.isEmpty() || inputRegularBoxProofs.isEmpty() || outputRegularBoxesData.isEmpty()){
            throw new IllegalArgumentException("Some of the input parameters are unacceptable!");
        }

        // Number of input ids should be equal to number of proofs, otherwise transaction is for sure invalid.
        if(inputRegularBoxIds.size() != inputRegularBoxProofs.size()){
            throw new IllegalArgumentException("Regular box inputs list size is different to proving signatures list size!");
        }

        this.inputRegularBoxIds = inputRegularBoxIds;
        this.inputRegularBoxProofs = inputRegularBoxProofs;
        this.outputRegularBoxesData = outputRegularBoxesData;
        this.fee = fee;
        this.timestamp = timestamp;
    }

    abstract protected List<NoncedBoxData<Proposition, NoncedBox<Proposition>>> getCustomOutputData();

    @Override
    final public List<NoncedBoxData<Proposition, NoncedBox<Proposition>>> getOutputData(){
        if(allBoxesData == null){
            allBoxesData = new ArrayList<>();
            // Add own regular boxes data
            for(RegularBoxData regularBoxData: outputRegularBoxesData){
                allBoxesData.add((NoncedBoxData) regularBoxData);
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
        // Fill the list with the regular inputs.
        for (int i = 0; i < inputRegularBoxIds.size() && i < inputRegularBoxProofs.size(); i++) {
            int finalI = i;
            BoxUnlocker<Proposition> unlocker = new BoxUnlocker<Proposition>() {
                @Override
                public byte[] closedBoxId() {
                    return inputRegularBoxIds.get(finalI);
                }

                @Override
                public Proof boxKey() {
                    return inputRegularBoxProofs.get(finalI);
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
    public long timestamp() {
        return timestamp;
    }

    @Override
    public boolean transactionSemanticValidity() {
        if(fee < 0 || timestamp < 0)
            return false;

        // check that we have enough proofs.
        if(inputRegularBoxIds.size() != inputRegularBoxProofs.size()) {
            return false;
        }

        return true;
    }
}
