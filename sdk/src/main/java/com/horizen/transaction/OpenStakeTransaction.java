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

import java.util.*;

import static com.horizen.transaction.CoreTransactionsIdsEnum.OpenStakeTransactionId;

/**
 * OpenStakeTransaction is used to open the forging stake to the world.
 * It can be used when the flag "restrictForger" is enabled and can be fired only by the allowed forgers inside the "allowedForgers" list.
 * This transaction has 1 input and 0 or 1 output. It contains a custom field "forgerIndex" that identify a specific forger inside the "allowedForgers" list.
 * The input must be locked by the corresponding proposition indexed by "forgerIndex" inside the "allowedForgers" list.
 */
public class OpenStakeTransaction extends SidechainNoncedTransaction<PublicKey25519Proposition, ZenBox, ZenBoxData>{
    public final static byte OPEN_STAKE_TRANSACTION_VERSION = 1;

    final byte[] inputsIds;
    private final Optional<ZenBoxData> outputsData;
    final Proof<Proposition> proofs;

    private final long fee;

    private final byte version;

    private List<BoxUnlocker<PublicKey25519Proposition>> unlockers;

    private final int forgerIndex;

    public OpenStakeTransaction(byte[] inputsIds,
                                Optional<ZenBoxData> outputsData,
                                Proof<Proposition> proofs,
                                int forgerIndex,
                                long fee,
                                byte version) {
        Objects.requireNonNull(inputsIds, "Inputs Ids can't be null.");

        this.inputsIds = inputsIds;
        this.outputsData = outputsData;
        this.proofs = proofs;
        this.forgerIndex = forgerIndex;
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
            BoxUnlocker<PublicKey25519Proposition> unlocker = new BoxUnlocker() {
                @Override
                public byte[] closedBoxId() {
                    return inputsIds;
                }

                @Override
                public Proof boxKey() {
                    return proofs;
                }
            };
            unlockers.add(unlocker);
        }

        return Collections.unmodifiableList(unlockers);
    }

    @Override
    protected List<ZenBoxData> getOutputData(){
        ArrayList<ZenBoxData> output = new ArrayList();
        outputsData.ifPresent(output::add);
        return output;
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

        if (inputsIds == null)
            throw new TransactionSemanticValidityException(String.format("Transaction [%s] is semantically invalid: " +
                    "no input data present.", id()));

        if (proofs == null)
            throw new TransactionSemanticValidityException(String.format("Transaction [%s] is semantically invalid: " +
                    "no proof data present.", id()));

        if (forgerIndex < 0) {
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
        return Ints.toByteArray(this.forgerIndex);
    }

    public int getForgerIndex() { return this.forgerIndex; }

    public TransactionIncompatibilityChecker incompatibilityChecker() {
        return new OpenStakeTransactionIncompatibilityChecker();
    }


    public static OpenStakeTransaction create(Pair<ZenBox, PrivateKey25519> from,
                                            Optional<ZenBoxData> outputs,
                                            int forgerIndex,
                                            long fee) throws TransactionSemanticValidityException {
        if(from == null || outputs.isEmpty())
            throw new IllegalArgumentException("Parameters can't be null.");

        OpenStakeTransaction unsignedTransaction = new OpenStakeTransaction(from.getKey().id(), outputs, null, forgerIndex, fee, OPEN_STAKE_TRANSACTION_VERSION);

        byte[] messageToSign = unsignedTransaction.messageToSign();
        Secret secret = from.getValue();

        OpenStakeTransaction transaction = new OpenStakeTransaction(from.getKey().id(), outputs, (Proof<Proposition>) secret.sign(messageToSign), forgerIndex, fee, OPEN_STAKE_TRANSACTION_VERSION);
        transaction.transactionSemanticValidity();

        return transaction;
    }
}
