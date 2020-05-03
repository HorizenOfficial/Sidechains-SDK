package com.horizen.transaction;

import com.google.common.primitives.Bytes;
import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;
import com.horizen.box.*;
import com.horizen.box.data.*;
import com.horizen.proof.Proof;
import com.horizen.proposition.*;
import com.horizen.proof.Signature25519;
import com.horizen.proof.Signature25519Serializer;
import com.horizen.secret.PrivateKey25519;
import com.horizen.utils.DynamicTypedSerializer;
import com.horizen.utils.ListSerializer;
import com.horizen.utils.BytesUtils;
import com.horizen.utils.Pair;

import java.util.*;

import static com.horizen.box.CoreBoxesIdsEnum.ForgerBoxId;
import static com.horizen.box.CoreBoxesIdsEnum.RegularBoxId;
import static com.horizen.box.CoreBoxesIdsEnum.WithdrawalRequestBoxId;
import static com.horizen.transaction.CoreTransactionsIdsEnum.RegularTransactionId;

public final class RegularTransaction
    extends SidechainTransaction<Proposition, NoncedBox<Proposition>>
{
    private List<RegularBox> inputs;
    private List<NoncedBoxData<? extends Proposition, ? extends NoncedBox<? extends Proposition>>> outputs;
    private List<Signature25519> signatures;

    private long fee;
    private long timestamp;

    private List<NoncedBox<Proposition>> newBoxes;
    private List<BoxUnlocker<Proposition>> unlockers;

    // Serializers definition
    private static ListSerializer<RegularBox> boxListSerializer =
            new ListSerializer<>(RegularBoxSerializer.getSerializer(), MAX_TRANSACTION_UNLOCKERS);
    private static ListSerializer<NoncedBoxData<? extends Proposition, ? extends NoncedBox<? extends Proposition>>> boxDataListSerializer =
            new ListSerializer<>(new DynamicTypedSerializer<>(
                    new HashMap<Byte, NoncedBoxDataSerializer>() {{
                        put(RegularBoxId.id(), RegularBoxDataSerializer.getSerializer());
                        put(WithdrawalRequestBoxId.id(), WithdrawalRequestBoxDataSerializer.getSerializer());
                        put(ForgerBoxId.id(), ForgerBoxDataSerializer.getSerializer());
                    }}, new HashMap<>()
            ));
    private static ListSerializer<Signature25519> signaturesSerializer =
            new ListSerializer<>(Signature25519Serializer.getSerializer(), MAX_TRANSACTION_UNLOCKERS);

    private RegularTransaction(List<RegularBox> inputs,
                               List<NoncedBoxData<? extends Proposition, ? extends NoncedBox<? extends Proposition>>> outputs,
                               List<Signature25519> signatures,
                               long fee,
                               long timestamp) {
        if(inputs.size() != signatures.size())
            throw new IllegalArgumentException("Inputs list size is different to signatures list size!");
        this.inputs = inputs;
        this.outputs = outputs;
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
    public synchronized List<NoncedBox<Proposition>> newBoxes() {
        if(newBoxes == null) {
            newBoxes = new ArrayList<>();
            for (int i = 0; i < outputs.size(); i++) {
                long nonce = getNewBoxNonce(outputs.get(i).proposition(), i);
                NoncedBoxData boxData = outputs.get(i);
                if(boxData instanceof RegularBoxData) {
                    newBoxes.add((NoncedBox)new RegularBox((RegularBoxData) boxData, nonce));
                } else if(boxData instanceof WithdrawalRequestBoxData) {
                    newBoxes.add((NoncedBox)new WithdrawalRequestBox((WithdrawalRequestBoxData) boxData, nonce));
                } else if(boxData instanceof ForgerBoxData) {
                    newBoxes.add((NoncedBox)new ForgerBox((ForgerBoxData) boxData, nonce));
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

        // check supported new boxes data
        if(!checkSupportedBoxDataTypes(outputs))
            return false;

        Long outputsAmount = 0L;
        for(NoncedBoxData output: outputs) {
            if (output.value() <= 0)
                return false;
            outputsAmount += output.value();
        }

        Long inputsAmount = 0L;
        for(int i = 0; i < inputs.size(); i++) {
            if (!signatures.get(i).isValid(inputs.get(i).proposition(), messageToSign()))
                return false;
            inputsAmount += inputs.get(i).value();
        }

        if(inputsAmount != outputsAmount + fee)
            return false;

        return true;
    }

    @Override
    public byte transactionTypeId() {
        return RegularTransactionId.id();
    }

    @Override
    public byte[] bytes() {
        byte[] inputBoxesBytes = boxListSerializer.toBytes(inputs);
        byte[] outputBoxDataBytes = boxDataListSerializer.toBytes(outputs);

        byte[] signaturesBytes = signaturesSerializer.toBytes(signatures);

        return Bytes.concat(                                            // minimum RegularTransaction length is 40 bytes
                Longs.toByteArray(fee()),                       // 8 bytes
                Longs.toByteArray(timestamp()),                         // 8 bytes
                Ints.toByteArray(inputBoxesBytes.length),               // 4 bytes
                inputBoxesBytes,                                        // depends on previous value (>=4 bytes)
                Ints.toByteArray(outputBoxDataBytes.length),            // 4 bytes
                outputBoxDataBytes,                                     // depends on previous value (>=4 bytes)
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

        List<RegularBox> inputs = boxListSerializer.parseBytes(Arrays.copyOfRange(bytes, offset, offset + batchSize));
        offset += batchSize;

        batchSize = BytesUtils.getInt(bytes, offset);
        offset += 4;

        List<NoncedBoxData<? extends Proposition, ? extends NoncedBox<? extends Proposition>>> outputs = boxDataListSerializer.parseBytes(Arrays.copyOfRange(bytes, offset, offset + batchSize));
        offset += batchSize;

        batchSize = BytesUtils.getInt(bytes, offset);
        offset += 4;
        if(bytes.length != offset + batchSize)
            throw new IllegalArgumentException("Input data corrupted.");

        List<Signature25519> signatures = signaturesSerializer.parseBytes(Arrays.copyOfRange(bytes, offset, offset + batchSize));

        return new RegularTransaction(inputs, outputs, signatures, fee, timestamp);
    }

    private static Boolean checkSupportedBoxDataTypes(List<NoncedBoxData<? extends Proposition, ? extends NoncedBox<? extends Proposition>>> boxDataList) {
        for(NoncedBoxData boxData: boxDataList) {
            if (!(boxData instanceof RegularBoxData)
                    && !(boxData instanceof WithdrawalRequestBoxData)
                    && !(boxData instanceof ForgerBoxData)
                    )
                return false;
        }
        return true;
    }

    public static RegularTransaction create(List<Pair<RegularBox, PrivateKey25519>> from,
                                            List<NoncedBoxData<? extends Proposition, ? extends NoncedBox<? extends Proposition>>> outputs,
                                            long fee,
                                            long timestamp) {
        if(from == null || outputs == null)
            throw new IllegalArgumentException("Parameters can't be null.");
        if(from.size() > MAX_TRANSACTION_UNLOCKERS)
            throw new IllegalArgumentException("Transaction from number is too large.");
        if(outputs.size() > MAX_TRANSACTION_NEW_BOXES)
            throw new IllegalArgumentException("Transaction outputs number is too large.");
        if(!checkSupportedBoxDataTypes(outputs))
            throw new IllegalArgumentException("Unsupported output box data type found.");

        List<RegularBox> inputs = new ArrayList<>();
        List<Signature25519> fakeSignatures = new ArrayList<>();
        for(Pair<RegularBox, PrivateKey25519> item : from) {
            inputs.add(item.getKey());
            fakeSignatures.add(null);
        }

        RegularTransaction unsignedTransaction = new RegularTransaction(inputs, outputs, fakeSignatures, fee, timestamp);

        byte[] messageToSign = unsignedTransaction.messageToSign();
        List<Signature25519> signatures = new ArrayList<>();
        for(Pair<RegularBox, PrivateKey25519> item : from) {
            signatures.add(item.getValue().sign(messageToSign));
        }

        RegularTransaction transaction = new RegularTransaction(inputs, outputs, signatures, fee, timestamp);
        if(!transaction.semanticValidity())
            throw new IllegalArgumentException("Created transaction is semantically invalid.");
        return transaction;
    }

    @Override
    public String toString() {
        return "RegularTransaction{" +
                "inputs=" + inputs +
                ", outputs=" + outputs +
                ", signatures=" + signatures +
                ", fee=" + fee +
                ", timestamp=" + timestamp +
                ", newBoxes=" + newBoxes +
                ", unlockers=" + unlockers +
                '}';
    }
}
