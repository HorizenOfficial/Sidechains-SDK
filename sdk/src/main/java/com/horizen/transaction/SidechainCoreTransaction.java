package com.horizen.transaction;

import com.google.common.primitives.Bytes;
import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;
import com.horizen.box.*;
import com.horizen.box.data.*;
import com.horizen.proof.Proof;
import com.horizen.proof.ProofSerializer;
import com.horizen.proof.Signature25519Serializer;
import com.horizen.proposition.Proposition;
import com.horizen.transaction.exception.TransactionSemanticValidityException;
import com.horizen.utils.BytesUtils;
import com.horizen.utils.DynamicTypedSerializer;
import com.horizen.utils.ListSerializer;
import scala.Array;
import scorex.core.NodeViewModifier$;

import static com.horizen.transaction.CoreTransactionsIdsEnum.SidechainCoreTransactionId;

import java.io.ByteArrayOutputStream;
import java.util.*;


public final class SidechainCoreTransaction
        extends SidechainNoncedTransaction<Proposition, Box<Proposition>, NoncedBoxData<Proposition, Box<Proposition>>>
{
    public final static byte SIDECHAIN_CORE_TRANSACTION_VERSION = 1;

    private final List<byte[]> inputsIds;
    private final List<NoncedBoxData<Proposition, Box<Proposition>>> outputsData;
    private final List<Proof<Proposition>> proofs;

    // Serializers definition
    private final static ListSerializer<NoncedBoxData<Proposition, Box<Proposition>>> boxesDataSerializer = new ListSerializer<>(
            new DynamicTypedSerializer<>(new HashMap<Byte, NoncedBoxDataSerializer>() {{
                put((byte)1, ZenBoxDataSerializer.getSerializer());
                put((byte)2, WithdrawalRequestBoxDataSerializer.getSerializer());
                put((byte)3, ForgerBoxDataSerializer.getSerializer());
            }}, new HashMap<>()), MAX_TRANSACTION_UNLOCKERS);

    private final static ListSerializer<Proof<Proposition>> proofsSerializer = new ListSerializer<>(
            new DynamicTypedSerializer<>(new HashMap<Byte, ProofSerializer>() {{
                put((byte)1, Signature25519Serializer.getSerializer());
            }}, new HashMap<>()), MAX_TRANSACTION_UNLOCKERS);

    private final long fee;

    private final byte version;

    private List<BoxUnlocker<Proposition>> unlockers;


    public SidechainCoreTransaction(List<byte[]> inputsIds,
                             List<NoncedBoxData<Proposition, Box<Proposition>>> outputsData,
                             List<Proof<Proposition>> proofs,
                             long fee,
                             byte version) {
        Objects.requireNonNull(inputsIds, "Inputs Ids list can't be null.");
        Objects.requireNonNull(outputsData, "Outputs Data list can't be null.");
        Objects.requireNonNull(proofs, "Proofs list can't be null.");
        // Do we need to care about inputs ids length here or state/serialization check is enough?

        this.inputsIds = inputsIds;
        this.outputsData = outputsData;
        this.proofs = proofs;
        this.fee = fee;
        this.version = version;
    }

    @Override
    public TransactionSerializer serializer() {
        return SidechainCoreTransactionSerializer.getSerializer();
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
    protected List<NoncedBoxData<Proposition, Box<Proposition>>> getOutputData(){
        return outputsData;
    }

    @Override
    public long fee() {
        return fee;
    }

    @Override
    public void transactionSemanticValidity() throws TransactionSemanticValidityException {
        if (version != SIDECHAIN_CORE_TRANSACTION_VERSION) {
            throw new TransactionSemanticValidityException(String.format("Transaction [%s] is semantically invalid: " +
                    "unsupported version number.", id()));
        }

        if(inputsIds.isEmpty() || outputsData.isEmpty())
            throw new TransactionSemanticValidityException(String.format("Transaction [%s] is semantically invalid: " +
                    "no input and output data present.", id()));

        // check that we have enough proofs and try to open each box only once.
        if(inputsIds.size() != proofs.size() || inputsIds.size() != boxIdsToOpen().size())
            throw new TransactionSemanticValidityException(String.format("Transaction [%s] is semantically invalid: " +
                    "inputs number is not consistent to proofs number.", id()));
    }

    @Override
    public byte transactionTypeId() {
        return SidechainCoreTransactionId.id();
    }

    @Override
    public byte version() {
        return version;
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
    public byte[] bytes() {
        ByteArrayOutputStream inputsIdsStream = new ByteArrayOutputStream();
        for(byte[] id: inputsIds)
            inputsIdsStream.write(id, 0, id.length);

        byte[] inputIdsBytes = inputsIdsStream.toByteArray();

        byte[] outputBoxDataBytes = boxesDataSerializer.toBytes(outputsData);

        byte[] proofsBytes = proofsSerializer.toBytes(proofs);

        return Bytes.concat(                                        // minimum SidechainCoreTransaction length is 29 bytes
                new byte[] {version()},                             // 1 byte
                Longs.toByteArray(fee()),                           // 8 bytes
                Ints.toByteArray(inputIdsBytes.length),             // 4 bytes
                inputIdsBytes,                                      // depends in previous value(>=0 bytes)
                Ints.toByteArray(outputBoxDataBytes.length),        // 4 bytes
                outputBoxDataBytes,                                 // depends on previous value (>=4 bytes)
                Ints.toByteArray(proofsBytes.length),               // 4 bytes
                proofsBytes                                         // depends on previous value (>=4 bytes)
        );
    }

    public static SidechainCoreTransaction parseBytes(byte[] bytes) {
        if(bytes.length < 29)
            throw new IllegalArgumentException("Input data corrupted.");

        if(bytes.length > MAX_TRANSACTION_SIZE)
            throw new IllegalArgumentException("Input data length is too large.");

        int offset = 0;

        byte version = bytes[offset];
        offset += 1;

        if (version != SIDECHAIN_CORE_TRANSACTION_VERSION) {
            throw new IllegalArgumentException(String.format("Unsupported transaction version[%d].", version));
        }

        long fee = BytesUtils.getLong(bytes, offset);
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

        List<NoncedBoxData<Proposition, Box<Proposition>>> outputsData = boxesDataSerializer.parseBytes(Arrays.copyOfRange(bytes, offset, offset + batchSize));
        offset += batchSize;

        batchSize = BytesUtils.getInt(bytes, offset);
        offset += 4;
        if(bytes.length != offset + batchSize)
            throw new IllegalArgumentException("Input data corrupted.");

        List<Proof<Proposition>> proofs = proofsSerializer.parseBytes(Arrays.copyOfRange(bytes, offset, offset + batchSize));

        return new SidechainCoreTransaction(inputsIds, outputsData, proofs, fee, version);
    }
}
