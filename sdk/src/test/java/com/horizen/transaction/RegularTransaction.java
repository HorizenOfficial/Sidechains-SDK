package com.horizen.transaction;

import com.horizen.box.*;
import com.horizen.box.data.*;
import com.horizen.proof.Proof;
import com.horizen.proposition.*;
import com.horizen.proof.Signature25519;
import com.horizen.secret.PrivateKey25519;
import com.horizen.transaction.exception.TransactionSemanticValidityException;
import com.horizen.utils.Pair;
import scala.Array;
import java.util.*;
import java.util.stream.Collectors;


public final class RegularTransaction
    extends SidechainTransaction<Proposition, Box<Proposition>>
{
    List<ZenBox> inputs;
    List<BoxData<? extends Proposition, ? extends Box<? extends Proposition>>> outputs;
    List<Signature25519> signatures;

    private long fee;

    private List<Proposition> newBoxesPropositions;
    private List<Box<Proposition>> newBoxes;
    private List<BoxUnlocker<Proposition>> unlockers;

    RegularTransaction(List<ZenBox> inputs,
                               List<BoxData<? extends Proposition, ? extends Box<? extends Proposition>>> outputs,
                               List<Signature25519> signatures,
                               long fee) {
        if(inputs.size() != signatures.size())
            throw new IllegalArgumentException("Inputs list size is different to signatures list size!");
        this.inputs = inputs;
        this.outputs = outputs;
        this.signatures = signatures;
        this.fee = fee;
    }

    @Override
    public TransactionSerializer serializer() {
        return RegularTransactionSerializer.getSerializer();
    }

    @Override
    public synchronized List<BoxUnlocker<Proposition>> unlockers() {
        if(unlockers == null) {
            unlockers = new ArrayList<>();
            for (int i = 0; i < inputs.size() && i < signatures.size(); i++) {
                int finalI = i;
                BoxUnlocker<Proposition> unlocker = new BoxUnlocker<Proposition>() {
                    @Override
                    public byte[] closedBoxId() {
                        return inputs.get(finalI).id();
                    }

                    @Override
                    public Proof boxKey() {
                        return signatures.get(finalI);
                    }
                };
                unlockers.add(unlocker);
            }
        }

        return Collections.unmodifiableList(unlockers);
    }

    @Override
    public List<Proposition> newBoxesPropositions() {
        if(newBoxesPropositions == null){
            newBoxesPropositions = outputs.stream().map(BoxData::proposition).collect(Collectors.toList());
        }
        return Collections.unmodifiableList(newBoxesPropositions);
    }

    @Override
    public synchronized List<Box<Proposition>> newBoxes() {
        if(newBoxes == null) {
            newBoxes = new ArrayList<>();
            for (int i = 0; i < outputs.size(); i++) {
                long nonce = getNewBoxNonce(outputs.get(i).proposition(), i);
                BoxData boxData = outputs.get(i);
                if(boxData instanceof ZenBoxData) {
                    newBoxes.add((Box)new ZenBox((ZenBoxData) boxData, nonce));
                } else if(boxData instanceof WithdrawalRequestBoxData) {
                    newBoxes.add((Box)new WithdrawalRequestBox((WithdrawalRequestBoxData) boxData, nonce));
                } else if(boxData instanceof ForgerBoxData) {
                    newBoxes.add((Box)new ForgerBox((ForgerBoxData) boxData, nonce));
                } else // Never should happen.
                    throw new IllegalArgumentException(String.format("Unexpected BoxData type: %s", boxData.getClass().toString()));
            }
        }

        return Collections.unmodifiableList(newBoxes);
    }

    @Override
    public long fee() {
        return fee;
    }

    @Override
    public byte[] customFieldsData() {
        return Array.emptyByteArray();
    }

    @Override
    public byte[] customDataMessageToSign() {
        return Array.emptyByteArray();
    }

    @Override
    public void transactionSemanticValidity() throws TransactionSemanticValidityException {
        // check that we have enough proofs and try to open each box only once.
        if(inputs.size() != signatures.size() || inputs.size() != boxIdsToOpen().size())
            throw new TransactionSemanticValidityException(String.format("Transaction [%s] is semantically invalid: " +
                    "inputs number is not consistent to proofs number.", id()));

        // check supported new boxes data
        if(!checkSupportedBoxDataTypes(outputs))
            throw new TransactionSemanticValidityException(String.format("Transaction [%s] is semantically invalid: " +
                    "contains box data output of invalid type.", id()));

        long outputsAmount = 0L;
        for(BoxData output: outputs) {
            outputsAmount += output.value();
        }

        long inputsAmount = 0L;
        for(int i = 0; i < inputs.size(); i++) {
            if (!signatures.get(i).isValid(inputs.get(i).proposition(), messageToSign()))
                throw new TransactionSemanticValidityException(String.format("Transaction [%s] is semantically invalid: " +
                        "inputs input [%d] signature.", id(), i));
            inputsAmount += inputs.get(i).value();
        }

        if(inputsAmount != outputsAmount + fee)
            throw new TransactionSemanticValidityException(String.format("Transaction [%s] is semantically invalid: " +
                    "inconsistent inputs, outputs and fee amount.", id()));
    }

    @Override
    public byte transactionTypeId() {
        return 111;
    }

    @Override
    public byte version() {
        return 100;
    }


    private static Boolean checkSupportedBoxDataTypes(List<BoxData<? extends Proposition, ? extends Box<? extends Proposition>>> boxDataList) {
        for(BoxData boxData: boxDataList) {
            if (!(boxData instanceof ZenBoxData)
                    && !(boxData instanceof WithdrawalRequestBoxData)
                    && !(boxData instanceof ForgerBoxData)
                    )
                return false;
        }
        return true;
    }

    public static RegularTransaction create(List<Pair<ZenBox, PrivateKey25519>> from,
                                            List<BoxData<? extends Proposition, ? extends Box<? extends Proposition>>> outputs,
                                            long fee) {
        if(from == null || outputs == null)
            throw new IllegalArgumentException("Parameters can't be null.");
        if(from.size() > MAX_TRANSACTION_UNLOCKERS)
            throw new IllegalArgumentException("Transaction from number is too large.");
        if(outputs.size() > MAX_TRANSACTION_NEW_BOXES)
            throw new IllegalArgumentException("Transaction outputs number is too large.");
        if(!checkSupportedBoxDataTypes(outputs))
            throw new IllegalArgumentException("Unsupported output box data type found.");

        List<ZenBox> inputs = new ArrayList<>();
        List<Signature25519> fakeSignatures = new ArrayList<>();
        for(Pair<ZenBox, PrivateKey25519> item : from) {
            inputs.add(item.getKey());
            fakeSignatures.add(null);
        }

        RegularTransaction unsignedTransaction = new RegularTransaction(inputs, outputs, fakeSignatures, fee);

        byte[] messageToSign = unsignedTransaction.messageToSign();
        List<Signature25519> signatures = new ArrayList<>();
        for(Pair<ZenBox, PrivateKey25519> item : from) {
            signatures.add(item.getValue().sign(messageToSign));
        }

        RegularTransaction transaction = new RegularTransaction(inputs, outputs, signatures, fee);
        // We don't need to check semantic validity here.
        //if(!transaction.semanticValidity())
        //    throw new IllegalArgumentException("Created transaction is semantically invalid.");
        return transaction;
    }

    @Override
    public String toString() {
        return "RegularTransaction{" +
                "inputs=" + inputs +
                ", outputs=" + outputs +
                ", signatures=" + signatures +
                ", fee=" + fee +
                  ", newBoxes=" + newBoxes +
                ", unlockers=" + unlockers +
                '}';
    }
}
