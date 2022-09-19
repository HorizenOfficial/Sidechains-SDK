package com.horizen.transaction;

import com.horizen.box.Box;
import com.horizen.box.data.*;
import com.horizen.proof.Proof;
import com.horizen.proof.ProofSerializer;
import com.horizen.proof.Signature25519Serializer;
import com.horizen.proposition.Proposition;
import com.horizen.utils.BytesUtils;
import com.horizen.utils.DynamicTypedSerializer;
import com.horizen.utils.ListSerializer;
import sparkz.core.NodeViewModifier$;
import scorex.util.serialization.Reader;
import scorex.util.serialization.Writer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Random;

import static com.horizen.transaction.BoxTransaction.MAX_TRANSACTION_NEW_BOXES;
import static com.horizen.transaction.BoxTransaction.MAX_TRANSACTION_UNLOCKERS;
import static com.horizen.transaction.SidechainCoreTransaction.SIDECHAIN_CORE_TRANSACTION_VERSION;

public final class SidechainCoreTransactionExtendedSerializer implements TransactionSerializer<SidechainCoreTransactionExtended>
{
    private static SidechainCoreTransactionExtendedSerializer serializer;

    // Serializers definition
    private final static ListSerializer<BoxData<Proposition, Box<Proposition>>> boxesDataSerializer = new ListSerializer<>(
            new DynamicTypedSerializer<>(new HashMap<Byte, BoxDataSerializer>() {{
                put((byte)1, ZenBoxDataSerializer.getSerializer());
                put((byte)2, WithdrawalRequestBoxDataSerializer.getSerializer());
                put((byte)3, ForgerBoxDataSerializer.getSerializer());
            }}, new HashMap<>()), MAX_TRANSACTION_NEW_BOXES);

    private final static ListSerializer<Proof<Proposition>> proofsSerializer = new ListSerializer<>(
            new DynamicTypedSerializer<>(new HashMap<Byte, ProofSerializer>() {{
                put((byte)1, Signature25519Serializer.getSerializer());
            }}, new HashMap<>()), MAX_TRANSACTION_UNLOCKERS);

    static {
        serializer = new SidechainCoreTransactionExtendedSerializer();
    }

    private SidechainCoreTransactionExtendedSerializer() {
        super();
    }

    public static SidechainCoreTransactionExtendedSerializer getSerializer() {
        return serializer;
    }

    @Override
    public void serialize(SidechainCoreTransactionExtended transaction, Writer writer) {
        writer.put(transaction.version());
        writer.putLong(transaction.fee());
        writer.putInt(transaction.inputsIds.size());
        for (byte[] id: transaction.inputsIds) {
            writer.putBytes(id);
        }
        boxesDataSerializer.serialize(transaction.getOutputData(), writer);
        proofsSerializer.serialize(transaction.proofs, writer);

        int reaminingBytes = 1024 - writer.length();
        byte[] padding = new byte[reaminingBytes];
        new Random().nextBytes(padding);
        writer.putBytes(padding);
    }

    @Override
    public SidechainCoreTransactionExtended parse(Reader reader) {
        byte version = reader.getByte();

        if (version != SIDECHAIN_CORE_TRANSACTION_VERSION) {
            throw new IllegalArgumentException(String.format("Unsupported transaction version[%d].", version));
        }

        long fee = reader.getLong();
        int inputsNum = reader.getInt();

        ArrayList<byte[]> inputsIds = new ArrayList<>();
        for(int i = 0; i < inputsNum; i++) {
            inputsIds.add(reader.getBytes(NodeViewModifier$.MODULE$.ModifierIdSize()));
        }

        List<BoxData<Proposition, Box<Proposition>>> outputsData = boxesDataSerializer.parse(reader);
        List<Proof<Proposition>> proofs = proofsSerializer.parse(reader);
        return new SidechainCoreTransactionExtended(inputsIds, outputsData, proofs, fee, version);
    }
}
