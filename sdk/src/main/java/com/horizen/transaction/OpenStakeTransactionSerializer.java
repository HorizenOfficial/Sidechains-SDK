package com.horizen.transaction;

import com.horizen.box.data.ZenBoxData;
import com.horizen.box.data.ZenBoxDataSerializer;
import com.horizen.proof.Proof;
import com.horizen.proof.Signature25519Serializer;
import com.horizen.proposition.Proposition;
import com.horizen.utils.ListSerializer;
import scorex.core.NodeViewModifier$;
import scorex.util.serialization.Reader;
import scorex.util.serialization.Writer;

import java.util.ArrayList;
import java.util.List;

import static com.horizen.transaction.BoxTransaction.MAX_TRANSACTION_UNLOCKERS;
import static com.horizen.transaction.OpenStakeTransaction.OPEN_STAKE_TRANSACTION_VERSION;

public class OpenStakeTransactionSerializer implements TransactionSerializer<OpenStakeTransaction>
{
    private static OpenStakeTransactionSerializer serializer;

    private static ListSerializer<ZenBoxData> outputsSerializer = new ListSerializer(ZenBoxDataSerializer.getSerializer());

    private final static ListSerializer<Proof<Proposition>> proofsSerializer = new ListSerializer(Signature25519Serializer.getSerializer(), MAX_TRANSACTION_UNLOCKERS);

    static {
        serializer = new OpenStakeTransactionSerializer();
    }

    private OpenStakeTransactionSerializer() {
        super();
    }

    public static OpenStakeTransactionSerializer getSerializer() {
        return serializer;
    }

    @Override
    public void serialize(OpenStakeTransaction transaction, Writer writer) {
        writer.put(transaction.version());
        writer.putLong(transaction.fee());
        writer.putInt(transaction.inputsIds.size());
        for (byte[] id: transaction.inputsIds) {
            writer.putBytes(id);
        }
        outputsSerializer.serialize(transaction.getOutputData(), writer);
        proofsSerializer.serialize(transaction.proofs, writer);
        writer.putInt(transaction.getForgerListIndex());
    }

    @Override
    public OpenStakeTransaction parse(Reader reader) {
        byte version = reader.getByte();
        if (version != OPEN_STAKE_TRANSACTION_VERSION) {
            throw new IllegalArgumentException(String.format("Unsupported transaction version[%d].", version));
        }

        long fee = reader.getLong();
        int inputsNum = reader.getInt();

        ArrayList<byte[]> inputsIds = new ArrayList<>();
        for(int i = 0; i < inputsNum; i++) {
            inputsIds.add(reader.getBytes(NodeViewModifier$.MODULE$.ModifierIdSize()));
        }

        List<ZenBoxData> outputsData = outputsSerializer.parse(reader);
        List<Proof<Proposition>> proofs = proofsSerializer.parse(reader);
        int forgerListIndex = reader.getInt();

        return new OpenStakeTransaction(inputsIds, outputsData, proofs, forgerListIndex, fee, version);
    }
}
