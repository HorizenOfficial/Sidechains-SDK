package com.horizen.transaction;

import com.horizen.box.data.ZenBoxData;
import com.horizen.box.data.ZenBoxDataSerializer;
import com.horizen.proof.Proof;
import com.horizen.proof.ProofSerializer;
import com.horizen.proof.Signature25519Serializer;
import com.horizen.proposition.Proposition;
import com.horizen.utils.ListSerializer;
import scorex.core.NodeViewModifier$;
import scorex.util.serialization.Reader;
import scorex.util.serialization.Writer;

import java.util.List;
import java.util.Optional;

import static com.horizen.transaction.OpenStakeTransaction.OPEN_STAKE_TRANSACTION_VERSION;

public class OpenStakeTransactionSerializer implements TransactionSerializer<OpenStakeTransaction>
{
    private static OpenStakeTransactionSerializer serializer;

    private static ListSerializer<ZenBoxData> outputsSerializer = new ListSerializer(ZenBoxDataSerializer.getSerializer());

    private final static ProofSerializer proofsSerializer =  Signature25519Serializer.getSerializer();

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
        writer.putBytes(transaction.inputsIds);
        outputsSerializer.serialize(transaction.getOutputData(), writer);
        proofsSerializer.serialize(transaction.proofs, writer);
        writer.putInt(transaction.getForgerIndex());
    }

    @Override
    public OpenStakeTransaction parse(Reader reader) {
        byte version = reader.getByte();
        if (version != OPEN_STAKE_TRANSACTION_VERSION) {
            throw new IllegalArgumentException(String.format("Unsupported transaction version[%d].", version));
        }

        long fee = reader.getLong();

        byte[] inputsId = reader.getBytes(NodeViewModifier$.MODULE$.ModifierIdSize());

        List<ZenBoxData> outputsData = outputsSerializer.parse(reader);
        java.util.Optional<ZenBoxData> output = Optional.empty();
        if (outputsData.size() > 0) {
            output = Optional.of(outputsData.get(0));
        }

        Proof<Proposition> proof = proofsSerializer.parse(reader);
        int forgerListIndex = reader.getInt();

        return new OpenStakeTransaction(inputsId, output, proof, forgerListIndex, fee, version);
    }
}
