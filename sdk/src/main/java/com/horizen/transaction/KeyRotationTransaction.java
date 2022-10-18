package com.horizen.transaction;

import com.google.common.primitives.Ints;
import com.horizen.box.BoxUnlocker;
import com.horizen.box.ZenBox;
import com.horizen.box.data.ZenBoxData;
import com.horizen.certificatesubmitter.keys.KeyRotationProof;
import com.horizen.certificatesubmitter.keys.KeyRotationProofType;
import com.horizen.proof.Proof;
import com.horizen.proof.SchnorrProof;
import com.horizen.proof.Signature25519;
import com.horizen.proposition.PublicKey25519Proposition;
import com.horizen.proposition.SchnorrProposition;
import com.horizen.secret.PrivateKey25519;
import com.horizen.secret.Secret;
import com.horizen.transaction.exception.TransactionSemanticValidityException;
import com.horizen.utils.Pair;
import scala.Array;
import scala.Enumeration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static com.horizen.transaction.CoreTransactionsIdsEnum.OpenStakeTransactionId;

/*
 * KeyRotationTransaction is used for key rotation of signer or master key of certificate submitter
 */
public class KeyRotationTransaction extends SidechainNoncedTransaction<PublicKey25519Proposition, ZenBox, ZenBoxData> {
    public final static byte KEY_ROTATION_TRANSACTION_VERSION = 1;

    final byte[] inputId;
    private final Optional<ZenBoxData> outputData;
    final Signature25519 proof;

    private final long fee;

    private final byte version;

    private List<BoxUnlocker<PublicKey25519Proposition>> unlockers;

    private final KeyRotationProof keyRotationProof;

    public KeyRotationTransaction(byte[] inputId, Optional<ZenBoxData> outputData, Signature25519 proof, long fee, byte version, KeyRotationProof keyRotationProof) {
        this.inputId = inputId;
        this.outputData = outputData;
        this.proof = proof;
        this.fee = fee;
        this.version = version;
        this.keyRotationProof = keyRotationProof;
    }

    @Override
    public TransactionSerializer serializer() {
        return OpenStakeTransactionSerializer.getSerializer();
    }

    @Override
    public synchronized List<BoxUnlocker<PublicKey25519Proposition>> unlockers() {
        if (unlockers == null) {
            unlockers = new ArrayList<>();
            BoxUnlocker<PublicKey25519Proposition> unlocker = new BoxUnlocker() {
                @Override
                public byte[] closedBoxId() {
                    return inputId;
                }

                @Override
                public Proof boxKey() {
                    return proof;
                }
            };
            unlockers.add(unlocker);
        }

        return Collections.unmodifiableList(unlockers);
    }

    @Override
    protected List<ZenBoxData> getOutputData() {
        ArrayList<ZenBoxData> output = new ArrayList();
        outputData.ifPresent(output::add);
        return output;
    }

    @Override
    public long fee() {
        return this.fee;
    }

    @Override
    public void transactionSemanticValidity() throws TransactionSemanticValidityException {
        if (version != KEY_ROTATION_TRANSACTION_VERSION) {
            throw new TransactionSemanticValidityException(String.format("Transaction [%s] is semantically invalid: " +
                    "unsupported version number.", id()));
        }

        if (inputId == null)
            throw new TransactionSemanticValidityException(String.format("Transaction [%s] is semantically invalid: " +
                    "no input data present.", id()));

        if (proof == null)
            throw new TransactionSemanticValidityException(String.format("Transaction [%s] is semantically invalid: " +
                    "no proof data present.", id()));

        if (keyRotationProof.index() < 0) {
            throw new TransactionSemanticValidityException(String.format("Transaction [%s] is semantically invalid: " +
                    "forgerList index negative.", id()));
        }

        // TODO
        // old key and new key are different
        // some other checks
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
    public Boolean isCustom() {
        return false;
    }

    @Override
    public byte[] customFieldsData() {
        return Array.emptyByteArray();
    }

    @Override
    public byte[] customDataMessageToSign() {
        return Ints.toByteArray(this.keyRotationProof.index());
    }

    public int getForgerIndex() {
        return this.keyRotationProof.index();
    }

    public byte[] getInputId() {
        return this.inputId;
    }

    public Optional<ZenBoxData> getOutputBox() {
        return this.outputData;
    }

    public TransactionIncompatibilityChecker incompatibilityChecker() {
        return new OpenStakeTransactionIncompatibilityChecker();
    }


    public static KeyRotationTransaction create(Pair<ZenBox, PrivateKey25519> from,
                                                PublicKey25519Proposition changeAddress,
                                                int forgerIndex,
                                                long fee,
                                                int keyTypeEnumerationNumber,
                                                int indexOfKey,
                                                SchnorrProposition newValueOfKey,
                                                SchnorrProof signingKeySignature,
                                                SchnorrProof masterKeySignature
    ) throws TransactionSemanticValidityException {
        if (from == null)
            throw new IllegalArgumentException("Parameters can't be null.");
        if (from.getKey().value() < fee)
            throw new IllegalArgumentException("Fee can't be greater than the input!");

        Optional<ZenBoxData> output = Optional.empty();
        if (from.getKey().value() > fee) {
            output = Optional.of(new ZenBoxData(changeAddress, from.getKey().value() - fee));
        }
        OpenStakeTransaction unsignedTransaction = new OpenStakeTransaction(from.getKey().id(), output, null, forgerIndex, fee, KEY_ROTATION_TRANSACTION_VERSION);

        byte[] messageToSign = unsignedTransaction.messageToSign();
        Secret secret = from.getValue();

        Enumeration.Value keyRotationProofType = KeyRotationProofType.Value(keyTypeEnumerationNumber);
        KeyRotationProof keyRotationProof = new KeyRotationProof(keyRotationProofType, indexOfKey, newValueOfKey, signingKeySignature, masterKeySignature);
        KeyRotationTransaction transaction = new KeyRotationTransaction(from.getKey().id(), output, (Signature25519) secret.sign(messageToSign), fee, KEY_ROTATION_TRANSACTION_VERSION, keyRotationProof);
        transaction.transactionSemanticValidity();

        return transaction;
    }
}