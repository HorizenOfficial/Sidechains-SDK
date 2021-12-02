package com.horizen.transaction;

import com.horizen.box.Box;
import com.horizen.box.data.NoncedBoxData;
import com.horizen.proposition.Proposition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

abstract public class SidechainNoncedTransaction<P extends Proposition, B extends Box<P>, D extends NoncedBoxData<P, B>>
        extends SidechainTransaction<P, B> {

    private List<B> newBoxes;
    private List<P> newBoxesPropositions;

    // Returns a full output data list, from which the output boxes should be constructed
    protected abstract List<D> getOutputData();

    // Returns a list of propositions for all output boxes which should be created from output data in a current transaction
    @Override
    final protected List<P> newBoxesPropositions(){
        if(newBoxesPropositions == null){
            newBoxesPropositions = getOutputData().stream().map(NoncedBoxData::proposition).collect(Collectors.toList());
        }
        return Collections.unmodifiableList(newBoxesPropositions);
    }

    // Specify the output boxes.
    // Nonce calculation algorithm is deterministic. So it's forbidden to set nonce in different way.
    // The check for proper nonce is defined in SidechainTransaction.semanticValidity method.
    // Such an algorithm is needed to disallow box ids manipulation and different vulnerabilities related to this.
    @Override
    final public synchronized List<B> newBoxes() {
        if(newBoxes == null) {
            List<D> outputsData = getOutputData();
            newBoxes = new ArrayList<>();
            for (int i = 0; i < outputsData.size(); i++) {
                D boxData = outputsData.get(i);
                long nonce = getNewBoxNonce(boxData.proposition(), i);
                newBoxes.add(boxData.getBox(nonce));
            }
        }
        return Collections.unmodifiableList(newBoxes);
    }
}
