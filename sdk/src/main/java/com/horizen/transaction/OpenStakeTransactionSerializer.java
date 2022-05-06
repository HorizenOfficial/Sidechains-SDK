package com.horizen.transaction;

import com.horizen.box.data.ZenBoxData;
import com.horizen.box.data.ZenBoxDataSerializer;
import com.horizen.proof.Signature25519;
import com.horizen.proof.Signature25519Serializer;
import scorex.core.NodeViewModifier$;
import scorex.util.serialization.Reader;
import scorex.util.serialization.Writer;

import java.util.Optional;

import static com.horizen.transaction.OpenStakeTransaction.OPEN_STAKE_TRANSACTION_VERSION;

public class OpenStakeTransactionSerializer implements TransactionSerializer<OpenStakeTransaction>
{
    private static OpenStakeTransactionSerializer serializer;

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
        writer.putBytes(transaction.inputId);
        if(transaction.getOutputBox().isPresent()) {
            writer.putInt(1);
            ZenBoxDataSerializer.getSerializer().serialize(transaction.getOutputBox().get(), writer);
        } else {
            writer.putInt(0);
        }
        Signature25519Serializer.getSerializer().serialize(transaction.proof, writer);
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

        int outputDataIsPresent = reader.getInt();
        java.util.Optional<ZenBoxData> output = Optional.empty();
        if (outputDataIsPresent == 1) {
            output = Optional.of(ZenBoxDataSerializer.getSerializer().parse(reader));
        }

        Signature25519 proof = Signature25519Serializer.getSerializer().parse(reader);
        int forgerIndex = reader.getInt();

        return new OpenStakeTransaction(inputsId, output, proof, forgerIndex, fee, version);
    }
}
