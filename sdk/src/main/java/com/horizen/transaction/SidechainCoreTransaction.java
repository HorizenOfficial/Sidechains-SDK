package com.horizen.transaction;

import com.google.common.primitives.Bytes;
import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import com.horizen.box.*;
import com.horizen.box.data.*;
import com.horizen.companion.SidechainBoxesDataCompanion;
import com.horizen.companion.SidechainProofsCompanion;
import com.horizen.proof.Proof;
import com.horizen.proposition.Proposition;
import com.horizen.utils.BytesUtils;
import com.horizen.utils.ListSerializer;
import scorex.core.NodeViewModifier$;

import static com.horizen.transaction.CoreTransactionsIdsEnum.SidechainCoreTransactionId;

import java.io.ByteArrayOutputStream;
import java.util.*;


public class SidechainCoreTransaction
        extends SidechainTransaction<Proposition, NoncedBox<Proposition>>
{
    private List<byte[]> inputsIds;
    private List<NoncedBoxData<Proposition, NoncedBox<Proposition>>> outputsData;
    private List<Proof<Proposition>> proofs;

    private SidechainBoxesDataCompanion boxesDataCompanion;
    private SidechainProofsCompanion proofsCompanion;

    private long fee;
    private long timestamp;

    private List<NoncedBox<Proposition>> newBoxes;
    private List<BoxUnlocker<Proposition>> unlockers;


    @Inject
    SidechainCoreTransaction(@Assisted("inputIds") List<byte[]> inputsIds,
                             @Assisted("outputsData") List<NoncedBoxData<Proposition, NoncedBox<Proposition>>> outputsData,
                             @Assisted("proofs") List<Proof<Proposition>> proofs,
                             @Assisted("fee") long fee,
                             @Assisted("timestamp") long timestamp,
                             SidechainBoxesDataCompanion boxesDataCompanion,
                             SidechainProofsCompanion proofsCompanion) {
        Objects.requireNonNull(inputsIds, "Inputs Ids list can't be null.");
        Objects.requireNonNull(outputsData, "Outputs Data list can't be null.");
        Objects.requireNonNull(proofs, "Proofs list can't be null.");
        Objects.requireNonNull(boxesDataCompanion, "BoxesDataCompanion can't be null.");
        Objects.requireNonNull(proofsCompanion, "ProofsCompanion can't be null.");
        // Do we need to care about inputs ids length here or state/serialization check is enough?

        this.inputsIds = inputsIds;
        this.outputsData = outputsData;
        this.proofs = proofs;
        this.fee = fee;
        this.timestamp = timestamp;
        this.boxesDataCompanion = boxesDataCompanion;
        this.proofsCompanion = proofsCompanion;
    }

    @Override
    public TransactionSerializer serializer() {
        return new SidechainCoreTransactionSerializer(boxesDataCompanion, proofsCompanion);
    }

    @Override
    public synchronized List<BoxUnlocker<Proposition>> unlockers() {
        if(unlockers == null) {
            unlockers = new ArrayList<>();
            for (int i = 0; i < inputsIds.size() && i < proofs.size(); i++) {
                int finalI = i;
                BoxUnlocker<Proposition> unlocker = new BoxUnlocker<Proposition>() {
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
    public synchronized List<NoncedBox<Proposition>> newBoxes() {
        if(newBoxes == null) {
            newBoxes = new ArrayList<>();
            for (int i = 0; i < outputsData.size(); i++) {
                NoncedBoxData boxData = outputsData.get(i);
                long nonce = getNewBoxNonce(boxData.proposition(), i);
                newBoxes.add(boxData.getBox(nonce));
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

        if(inputsIds.isEmpty() || outputsData.isEmpty())
            return false;

        // check that we have enough proofs and try to open each box only once.
        if(inputsIds.size() != proofs.size() || inputsIds.size() != boxIdsToOpen().size())
            return false;

        return true;
    }

    @Override
    public byte transactionTypeId() {
        return SidechainCoreTransactionId.id();
    }

    @Override
    public byte[] bytes() {
        ByteArrayOutputStream inputsIdsStream = new ByteArrayOutputStream();
        for(byte[] id: inputsIds)
            inputsIdsStream.write(id, 0, id.length);

        byte[] inputIdsBytes = inputsIdsStream.toByteArray();

        ListSerializer<NoncedBoxData<Proposition, NoncedBox<Proposition>>> boxesDataSerializer = new ListSerializer<>(boxesDataCompanion, MAX_TRANSACTION_NEW_BOXES);
        byte[] outputBoxDataBytes = boxesDataSerializer.toBytes(outputsData);

        ListSerializer<Proof<Proposition>> proofsSerializer = new ListSerializer<>(proofsCompanion, MAX_TRANSACTION_UNLOCKERS);
        byte[] proofsBytes = proofsSerializer.toBytes(proofs);

        return Bytes.concat(                                        // minimum SidechainCoreTransaction length is 36 bytes
                Longs.toByteArray(fee()),                           // 8 bytes
                Longs.toByteArray(timestamp()),                     // 8 bytes
                Ints.toByteArray(inputIdsBytes.length),             // 4 bytes
                inputIdsBytes,                                      // depends in previous value(>=0 bytes)
                Ints.toByteArray(outputBoxDataBytes.length),        // 4 bytes
                outputBoxDataBytes,                                 // depends on previous value (>=4 bytes)
                Ints.toByteArray(proofsBytes.length),               // 4 bytes
                proofsBytes                                         // depends on previous value (>=4 bytes)
        );
    }

    public static SidechainCoreTransaction parseBytes(
            byte[] bytes,
            SidechainBoxesDataCompanion boxesDataCompanion,
            SidechainProofsCompanion proofsCompanion) {
        if(bytes.length < 36)
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

        ArrayList<byte[]> inputsIds = new ArrayList<>();
        while(batchSize > 0) {
            int idLength = NodeViewModifier$.MODULE$.ModifierIdSize();
            inputsIds.add(Arrays.copyOfRange(bytes, offset, offset + idLength));
            offset += idLength;
            batchSize -= idLength;
        }

        batchSize = BytesUtils.getInt(bytes, offset);
        offset += 4;

        ListSerializer<NoncedBoxData<Proposition, NoncedBox<Proposition>>> boxesDataSerializer = new ListSerializer<>(boxesDataCompanion, MAX_TRANSACTION_NEW_BOXES);
        List<NoncedBoxData<Proposition, NoncedBox<Proposition>>> outputsData = boxesDataSerializer.parseBytes(Arrays.copyOfRange(bytes, offset, offset + batchSize));
        offset += batchSize;

        batchSize = BytesUtils.getInt(bytes, offset);
        offset += 4;
        if(bytes.length != offset + batchSize)
            throw new IllegalArgumentException("Input data corrupted.");

        ListSerializer<Proof<Proposition>> proofsSerializer = new ListSerializer<>(proofsCompanion, MAX_TRANSACTION_UNLOCKERS);
        List<Proof<Proposition>> proofs = proofsSerializer.parseBytes(Arrays.copyOfRange(bytes, offset, offset + batchSize));

        return new SidechainCoreTransaction(inputsIds, outputsData, proofs, fee, timestamp, boxesDataCompanion, proofsCompanion);
    }
}
