package com.horizen.transaction;

import com.google.common.primitives.Bytes;
import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;
import com.horizen.box.*;
import com.horizen.proof.Proof;
import com.horizen.proposition.*;
import com.horizen.proof.Signature25519;
import com.horizen.proof.Signature25519Serializer;
import com.horizen.secret.PrivateKey25519;
import com.horizen.utils.ListSerializer;
import com.horizen.utils.BytesUtils;
import com.horizen.utils.Pair;

import java.io.ByteArrayOutputStream;
import java.util.*;

public final class RegularTransaction
    extends SidechainTransaction<Proposition, NoncedBox<Proposition>>
{

    public static final byte TRANSACTION_TYPE_ID = 1;

    private List<RegularBox> inputs;
    private List<Pair<PublicKey25519Proposition, Long>> regularOutputs;
    private List<Pair<MCPublicKeyHashProposition, Long>> withdrawalOutputs;
    private List<Signature25519> signatures;

    private long fee;
    private long timestamp;

    private List<NoncedBox<Proposition>> newBoxes;
    private List<BoxUnlocker<Proposition>> unlockers;

    // Serializers definition
    private static ListSerializer<RegularBox> boxSerializer =
            new ListSerializer<>(RegularBoxSerializer.getSerializer(), MAX_TRANSACTION_UNLOCKERS);
    private static ListSerializer<PublicKey25519Proposition> regularPropositionSerializer =
            new ListSerializer<>(PublicKey25519PropositionSerializer.getSerializer(), MAX_TRANSACTION_NEW_BOXES);
    private static ListSerializer<MCPublicKeyHashProposition> withdrawalPropositionSerializer =
            new ListSerializer<>(MCPublicKeyHashPropositionSerializer.getSerializer(), MAX_TRANSACTION_UNLOCKERS);
    private static ListSerializer<Signature25519> signaturesSerializer =
            new ListSerializer<>(Signature25519Serializer.getSerializer(), MAX_TRANSACTION_UNLOCKERS);

    private RegularTransaction(List<RegularBox> inputs,
                               List<Pair<PublicKey25519Proposition, Long>> outputs,
                               List<Pair<MCPublicKeyHashProposition, Long>> withdrawalRequests,
                               List<Signature25519> signatures,
                               long fee,
                               long timestamp) {
        if(inputs.size() != signatures.size())
            throw new IllegalArgumentException("Inputs list size is different to signatures list size!");
        this.inputs = inputs;
        this.regularOutputs = outputs;
        this.withdrawalOutputs = withdrawalRequests;
        this.signatures = signatures;
        this.fee = fee;
        this.timestamp = timestamp;
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
                BoxUnlocker unlocker = new BoxUnlocker() {
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
    public synchronized List<NoncedBox<Proposition>> newBoxes() {
        if(newBoxes == null) {
            newBoxes = new ArrayList<>();
            int boxIndex = 0;
            for (int i = 0; i < regularOutputs.size(); i++, boxIndex++) {
                NoncedBox box = new RegularBox(regularOutputs.get(i).getKey(), getNewBoxNonce(regularOutputs.get(i).getKey(), boxIndex),
                        regularOutputs.get(i).getValue());
                newBoxes.add(box);
            }
            for (int i = 0; i < withdrawalOutputs.size(); i++, boxIndex++) {
                NoncedBox box = new WithdrawalRequestBox(withdrawalOutputs.get(i).getKey(),
                        getNewBoxNonce(withdrawalOutputs.get(i).getKey(), boxIndex),
                        withdrawalOutputs.get(i).getValue());
                newBoxes.add(box);
            }
        }

        return Collections.unmodifiableList(newBoxes);
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

        // check that we have enough proofs and try to open each box only once.
        if(inputs.size() != signatures.size() || inputs.size() != boxIdsToOpen().size())
            return false;

        for(Pair<PublicKey25519Proposition, Long> output : regularOutputs)
            if(output.getValue() <= 0)
                return false;

        for(Pair<MCPublicKeyHashProposition, Long> withdrawalRequest : withdrawalOutputs)
            if(withdrawalRequest.getValue() <= 0)
                return false;

        for(int i = 0; i < inputs.size(); i++) {
            if (!signatures.get(i).isValid(inputs.get(i).proposition(), messageToSign()))
                return false;
        }

        return true;
    }

    @Override
    public byte transactionTypeId() {
        return TRANSACTION_TYPE_ID;
    }

    @Override
    public byte[] bytes() {
        byte[] inputBoxesBytes = boxSerializer.toBytes(inputs);

        List<PublicKey25519Proposition> outputPropositions = new ArrayList<>();
        ByteArrayOutputStream outputValuesBytes = new ByteArrayOutputStream();
        for(Pair<PublicKey25519Proposition, Long> pair : regularOutputs) {
            outputPropositions.add(pair.getKey());
            outputValuesBytes.write(Longs.toByteArray(pair.getValue()), 0,8);
        }
        byte[] outputPropositionsBytes = regularPropositionSerializer.toBytes(outputPropositions);

        List<MCPublicKeyHashProposition> withdrawalPropositions = new ArrayList<>();
        ByteArrayOutputStream withdrawalValuesBytes = new ByteArrayOutputStream();
        for(Pair<MCPublicKeyHashProposition, Long> pair : withdrawalOutputs) {
            withdrawalPropositions.add(pair.getKey());
            withdrawalValuesBytes.write(Longs.toByteArray(pair.getValue()), 0,8);
        }
        byte[] withdrawalPropositionsBytes = withdrawalPropositionSerializer.toBytes(withdrawalPropositions);

        byte[] signaturesBytes = signaturesSerializer.toBytes(signatures);

        return Bytes.concat(                                            // minimum RegularTransaction length is 40 bytes
                Longs.toByteArray(fee()),                               // 8 bytes
                Longs.toByteArray(timestamp()),                         // 8 bytes
                Ints.toByteArray(inputBoxesBytes.length),               // 4 bytes
                inputBoxesBytes,                                        // depends on previous value (>=4 bytes)
                Ints.toByteArray(outputPropositionsBytes.length),       // 4 bytes
                outputPropositionsBytes,                                // depends on previous value (>=4 bytes)
                outputValuesBytes.toByteArray(),                        // depends on outputPropositions count (>=0 bytes)
                Ints.toByteArray(withdrawalPropositionsBytes.length),   // 4 bytes
                withdrawalPropositionsBytes,                            // depends on previous value (>=4 bytes)
                withdrawalValuesBytes.toByteArray(),                    // depends on withdrawalPropositions count (>=0 bytes)
                Ints.toByteArray(signaturesBytes.length),               // 4 bytes
                signaturesBytes                                         // depends on previous value (>=4 bytes)
        );
    }

    public static RegularTransaction parseBytes(byte[] bytes) {
        if(bytes.length < 40)
            throw new IllegalArgumentException("Input data corrupted.");

        if(bytes.length > MAX_TRANSACTION_SIZE)
            throw new IllegalArgumentException("Input data length is too large.");

        int offset = 0;

        long fee = BytesUtils.getLong(bytes, offset);
        offset += 8;

        long timestamp = BytesUtils.getLong(bytes, offset);
        offset += 8;

        int batchSize = BytesUtils.getInt(bytes, offset);
        offset += 4;

        List<RegularBox> inputs = boxSerializer.parseBytes(Arrays.copyOfRange(bytes, offset, offset + batchSize));
        offset += batchSize;

        batchSize = BytesUtils.getInt(bytes, offset);
        offset += 4;

        List<PublicKey25519Proposition> outputPropositions = regularPropositionSerializer.parseBytes(Arrays.copyOfRange(bytes, offset, offset + batchSize));
        offset += batchSize;

        List<Pair<PublicKey25519Proposition, Long>> outputs =  new ArrayList<>();
        for(PublicKey25519Proposition proposition : outputPropositions) {
            outputs.add(new Pair<>(proposition, BytesUtils.getLong(bytes, offset)));
            offset += 8;
        }

        batchSize = BytesUtils.getInt(bytes, offset);
        offset += 4;

        List<MCPublicKeyHashProposition> withdrawalPropositions = withdrawalPropositionSerializer.parseBytes(Arrays.copyOfRange(bytes, offset, offset + batchSize));
        offset += batchSize;

        List<Pair<MCPublicKeyHashProposition, Long>> withdrawalRequests =  new ArrayList<>();
        for(MCPublicKeyHashProposition proposition : withdrawalPropositions) {
            withdrawalRequests.add(new Pair<>(proposition, BytesUtils.getLong(bytes, offset)));
            offset += 8;
        }

        batchSize = BytesUtils.getInt(bytes, offset);
        offset += 4;
        if(bytes.length != offset + batchSize)
            throw new IllegalArgumentException("Input data corrupted.");

        List<Signature25519> signatures = signaturesSerializer.parseBytes(Arrays.copyOfRange(bytes, offset, offset + batchSize));

        return new RegularTransaction(inputs, outputs, withdrawalRequests, signatures, fee, timestamp);
    }

    public static RegularTransaction create(List<Pair<RegularBox, PrivateKey25519>> from,
                                            List<Pair<PublicKey25519Proposition, Long>> to,
                                            List<Pair<MCPublicKeyHashProposition, Long>> withdrawalRequests,
                                            long fee,
                                            long timestamp) {
        if(from == null || to == null || withdrawalRequests == null)
            throw new IllegalArgumentException("Parameters can't be null.");
        if(from.size() > MAX_TRANSACTION_UNLOCKERS)
            throw new IllegalArgumentException("Transaction from count is too large.");
        if(to.size() + withdrawalRequests.size() > MAX_TRANSACTION_NEW_BOXES)
            throw new IllegalArgumentException("Transaction to count is too large.");

        List<RegularBox> inputs = new ArrayList<>();
        List<Signature25519> fakeSignatures = new ArrayList<>();
        for(Pair<RegularBox, PrivateKey25519> item : from) {
            inputs.add(item.getKey());
            fakeSignatures.add(null);
        }

        RegularTransaction unsignedTransaction = new RegularTransaction(inputs, to, withdrawalRequests, fakeSignatures, fee, timestamp);

        byte[] messageToSign = unsignedTransaction.messageToSign();
        List<Signature25519> signatures = new ArrayList<>();
        for(Pair<RegularBox, PrivateKey25519> item : from) {
            signatures.add(item.getValue().sign(messageToSign));
        }

        RegularTransaction transaction = new RegularTransaction(inputs, to, withdrawalRequests, signatures, fee, timestamp);
        if(!transaction.semanticValidity())
            throw new IllegalArgumentException("Created transaction is semantically invalid.");
        return transaction;
    }

}
