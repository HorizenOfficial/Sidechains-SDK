package com.horizen.utxo.transaction;

import com.horizen.transaction.TransactionSerializer;
import com.horizen.utxo.box.BoxUnlocker;
import com.horizen.utxo.box.ZenBox;
import com.horizen.utxo.box.data.ZenBoxData;
import com.horizen.certificatesubmitter.keys.KeyRotationProof;
import com.horizen.certificatesubmitter.keys.KeyRotationProofTypes;
import com.horizen.proof.Proof;
import com.horizen.proof.SchnorrProof;
import com.horizen.proof.Signature25519;
import com.horizen.proposition.PublicKey25519Proposition;
import com.horizen.proposition.SchnorrProposition;
import com.horizen.secret.PrivateKey25519;
import com.horizen.secret.Secret;
import com.horizen.transaction.exception.TransactionSemanticValidityException;
import com.horizen.utils.Pair;
import org.bouncycastle.util.Arrays;
import scala.Array;
import scala.Enumeration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static com.horizen.utxo.transaction.CoreTransactionsIdsEnum.KeyRotationTransactionId;

/*
 * CertificateKeyRotationTransaction is used for key rotation of signer or master key of certificate submitter
 */
public class CertificateKeyRotationTransaction extends SidechainNoncedTransaction<PublicKey25519Proposition, ZenBox, ZenBoxData> {
    public final static byte CERTIFICATE_KEY_ROTATION_TRANSACTION_VERSION = 1;

    final byte[] inputId;
    final Optional<ZenBoxData> outputData;
    final Signature25519 proof;

    private final long fee;

    private final byte version;

    private List<BoxUnlocker<PublicKey25519Proposition>> unlockers;

    final KeyRotationProof keyRotationProof;

    private final SchnorrProof newKeySignature;

    public CertificateKeyRotationTransaction(byte[] inputId, Optional<ZenBoxData> outputData, Signature25519 proof, long fee, byte version, KeyRotationProof keyRotationProof, SchnorrProof newKeySignature) {
        this.inputId = inputId;
        this.outputData = outputData;
        this.proof = proof;
        this.fee = fee;
        this.version = version;
        this.keyRotationProof = keyRotationProof;
        this.newKeySignature = newKeySignature;
    }

    @Override
    public TransactionSerializer serializer() {
        return CertificateKeyRotationTransactionSerializer.getSerializer();
    }

    @Override
    public synchronized List<BoxUnlocker<PublicKey25519Proposition>> unlockers() {
        if (unlockers == null) {
            unlockers = new ArrayList<>();
            BoxUnlocker<PublicKey25519Proposition> unlocker = new BoxUnlocker<>() {
                @Override
                public byte[] closedBoxId() {
                    return inputId;
                }

                @Override
                public Proof<PublicKey25519Proposition> boxKey() {
                    return proof;
                }
            };
            unlockers.add(unlocker);
        }

        return Collections.unmodifiableList(unlockers);
    }

    @Override
    protected List<ZenBoxData> getOutputData() {
        ArrayList<ZenBoxData> output = new ArrayList<>();
        outputData.ifPresent(output::add);
        return output;
    }

    @Override
    public long fee() {
        return this.fee;
    }

    @Override
    public void transactionSemanticValidity() throws TransactionSemanticValidityException {
        if (version != CERTIFICATE_KEY_ROTATION_TRANSACTION_VERSION) {
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

        if (keyRotationProof.newKey() == null) {
            throw new TransactionSemanticValidityException(String.format("Transaction [%s] is semantically invalid: " +
                    "no newKey data present.", id()));
        }

        if (keyRotationProof.signingKeySignature() == null) {
            throw new TransactionSemanticValidityException(String.format("Transaction [%s] is semantically invalid: " +
                    "no signingKeySignature data present.", id()));
        }

        if (keyRotationProof.masterKeySignature() == null) {
            throw new TransactionSemanticValidityException(String.format("Transaction [%s] is semantically invalid: " +
                    "no masterKeySignature data present.", id()));
        }

        if (newKeySignature == null) {
            throw new TransactionSemanticValidityException(String.format("Transaction [%s] is semantically invalid: " +
                    "no newKeySignature data present.", id()));
        }
    }

    @Override
    public byte transactionTypeId() {
        return KeyRotationTransactionId.id();
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
        return Arrays.concatenate(
                this.keyRotationProof.bytes(),
                this.newKeySignature.bytes()
        );
    }

    public SchnorrProof getNewKeySignature() {
        return this.newKeySignature;
    }

    public KeyRotationProof getKeyRotationProof() {
        return this.keyRotationProof;
    }

    public TransactionIncompatibilityChecker incompatibilityChecker() {
        return new CertificateKeyRotationTransactionIncompatibilityChecker();
    }


    public static CertificateKeyRotationTransaction create(Pair<ZenBox, PrivateKey25519> from,
                                                           PublicKey25519Proposition changeAddress,
                                                           long fee,
                                                           int keyTypeEnumerationNumber,
                                                           int indexOfKey,
                                                           SchnorrProposition newKey,
                                                           SchnorrProof signingKeySignature,
                                                           SchnorrProof masterKeySignature,
                                                           SchnorrProof newKeySignature
    ) throws TransactionSemanticValidityException {
        if (from == null)
            throw new IllegalArgumentException("Parameters can't be null.");
        if (from.getKey().value() < fee)
            throw new IllegalArgumentException("Fee can't be greater than the input!");

        Optional<ZenBoxData> output = Optional.empty();
        if (from.getKey().value() > fee) {
            output = Optional.of(new ZenBoxData(changeAddress, from.getKey().value() - fee));
        }

        if(keyTypeEnumerationNumber < 0 || keyTypeEnumerationNumber >= KeyRotationProofTypes.maxId()) {
            throw new IllegalArgumentException("Key type enumeration value should be valid!");
        }
        Enumeration.Value keyRotationProofType = KeyRotationProofTypes.apply(keyTypeEnumerationNumber);
        KeyRotationProof keyRotationProof = new KeyRotationProof(keyRotationProofType, indexOfKey, newKey, signingKeySignature, masterKeySignature);

        CertificateKeyRotationTransaction unsignedTransaction = new CertificateKeyRotationTransaction(from.getKey().id(), output, null, fee, CERTIFICATE_KEY_ROTATION_TRANSACTION_VERSION, keyRotationProof, newKeySignature);

        byte[] messageToSign = unsignedTransaction.messageToSign();
        Secret secret = from.getValue();

        CertificateKeyRotationTransaction transaction = new CertificateKeyRotationTransaction(from.getKey().id(), output, (Signature25519) secret.sign(messageToSign), fee, CERTIFICATE_KEY_ROTATION_TRANSACTION_VERSION, keyRotationProof, newKeySignature);
        transaction.transactionSemanticValidity();

        return transaction;
    }
}