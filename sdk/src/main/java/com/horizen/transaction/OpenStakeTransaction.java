package com.horizen.transaction;

import com.google.common.primitives.Ints;
import com.horizen.box.BoxUnlocker;
import com.horizen.box.ZenBox;
import com.horizen.box.data.ZenBoxData;
import com.horizen.proof.Proof;
import com.horizen.proposition.Proposition;
import com.horizen.proposition.PublicKey25519Proposition;
import com.horizen.secret.PrivateKey25519;
import com.horizen.secret.Secret;
import com.horizen.transaction.exception.TransactionSemanticValidityException;
import com.horizen.utils.Pair;
import scala.Array;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import static com.horizen.transaction.CoreTransactionsIdsEnum.OpenStakeTransactionId;

public class OpenStakeTransaction extends SidechainNoncedTransaction<PublicKey25519Proposition, ZenBox, ZenBoxData>{
    public final static byte OPEN_STAKE_TRANSACTION_VERSION = 1;

    final List<byte[]> inputsIds;
    private final List<ZenBoxData> outputsData;
    final List<Proof<Proposition>> proofs;

    private final long fee;

    private final byte version;

    private List<BoxUnlocker<PublicKey25519Proposition>> unlockers;

    private final int forgerListIndex;

    public OpenStakeTransaction(List<byte[]> inputsIds,
                                List<ZenBoxData> outputsData,
                                List<Proof<Proposition>> proofs,
                                int forgerListIndex,
                                long fee,
                                byte version) {
        Objects.requireNonNull(inputsIds, "Inputs Ids list can't be null.");
        Objects.requireNonNull(outputsData, "Outputs Data list can't be null.");
        Objects.requireNonNull(proofs, "Proofs list can't be null.");

        this.inputsIds = inputsIds;
        this.outputsData = outputsData;
        this.proofs = proofs;
        this.forgerListIndex = forgerListIndex;
        this.fee = fee;
        this.version = version;
    }

    @Override
    public TransactionSerializer serializer() {
        return OpenStakeTransactionSerializer.getSerializer();
    }

    @Override
    public synchronized List<BoxUnlocker<PublicKey25519Proposition>> unlockers() {
        if(unlockers == null) {
            unlockers = new ArrayList<>();
            for (int i = 0; i < inputsIds.size() && i < proofs.size(); i++) {
                int finalI = i;
                BoxUnlocker<PublicKey25519Proposition> unlocker = new BoxUnlocker() {
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
    protected List<ZenBoxData> getOutputData(){
        return outputsData;
    }

    @Override
    public long fee() {
        return this.fee;
    }

    @Override
    public void transactionSemanticValidity() throws TransactionSemanticValidityException {
        if (version != OPEN_STAKE_TRANSACTION_VERSION) {
            throw new TransactionSemanticValidityException(String.format("Transaction [%s] is semantically invalid: " +
                    "unsupported version number.", id()));
        }

        if (inputsIds.isEmpty() || outputsData.isEmpty())
            throw new TransactionSemanticValidityException(String.format("Transaction [%s] is semantically invalid: " +
                    "no input and output data present.", id()));

        if (inputsIds.size() != 1) {
            throw new TransactionSemanticValidityException(String.format("Transaction [%s] is semantically invalid: " +
                    "input size != 1.", id()));
        }

        // check that we have enough proofs and try to open each box only once.
        if (inputsIds.size() != proofs.size() || inputsIds.size() != boxIdsToOpen().size())
            throw new TransactionSemanticValidityException(String.format("Transaction [%s] is semantically invalid: " +
                    "inputs number is not consistent to proofs number.", id()));

        if (forgerListIndex < 0) {
            throw new TransactionSemanticValidityException(String.format("Transaction [%s] is semantically invalid: " +
                    "forgerList index negative.", id()));
        }
    }

    @Override
    public byte transactionTypeId() {
        return OpenStakeTransactionId.id();
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
        return Ints.toByteArray(this.forgerListIndex);
    }

    public int getForgerListIndex() { return this.forgerListIndex; }

    public TransactionIncompatibilityChecker incompatibilityChecker() {
        return new OpenStakeTransactionIncompatibilityChecker();
    }


    public static OpenStakeTransaction create(List<Pair<ZenBox, PrivateKey25519>> from,
                                            List<ZenBoxData> outputs,
                                            int forgerIndex,
                                            long fee) throws TransactionSemanticValidityException {
        if(from == null || outputs == null)
            throw new IllegalArgumentException("Parameters can't be null.");
        if(from.size() > MAX_TRANSACTION_UNLOCKERS)
            throw new IllegalArgumentException("Transaction from number is too large.");
        if(outputs.size() > MAX_TRANSACTION_NEW_BOXES)
            throw new IllegalArgumentException("Transaction outputs number is too large.");

        List<byte[]> inputs = new ArrayList<>();
        List<Proof<Proposition>> fakeSignatures = new ArrayList<>();
        for(Pair<ZenBox, PrivateKey25519> item : from) {
            inputs.add(item.getKey().id());
            fakeSignatures.add(null);
        }

        OpenStakeTransaction unsignedTransaction = new OpenStakeTransaction(inputs, outputs, fakeSignatures, forgerIndex, fee, OPEN_STAKE_TRANSACTION_VERSION);

        byte[] messageToSign = unsignedTransaction.messageToSign();
        List<Proof<Proposition>> signatures = new ArrayList<>();
        for(Pair<ZenBox, PrivateKey25519> item : from) {
            Secret secret = item.getValue();
            signatures.add((Proof<Proposition>) secret.sign(messageToSign));
        }

        OpenStakeTransaction transaction = new OpenStakeTransaction(inputs, outputs, signatures, forgerIndex, fee, OPEN_STAKE_TRANSACTION_VERSION);
        transaction.transactionSemanticValidity();

        return transaction;
    }
}
