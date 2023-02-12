package com.horizen.transaction;

import com.horizen.box.data.ZenBoxData;
import com.horizen.box.data.ZenBoxDataSerializer;
import com.horizen.proof.Signature25519;
import com.horizen.proof.Signature25519Serializer;
import com.horizen.utils.Checker;
import sparkz.core.NodeViewModifier$;
import sparkz.util.serialization.Reader;
import sparkz.util.serialization.Writer;

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
        byte version = Checker.version(reader, OPEN_STAKE_TRANSACTION_VERSION, "transaction");

        long fee = Checker.readLongNotLessThanZero(reader, "transaction fee");

        byte[] inputsId = Checker.readBytes(reader, NodeViewModifier$.MODULE$.ModifierIdSize(), "input ids");

        int outputDataIsPresent = Checker.equalZeroOrOne(Checker.readIntNotLessThanZero(reader, )(), "output data is present");

        java.util.Optional<ZenBoxData> output = Optional.empty();
        if (outputDataIsPresent == 1) {
            output = Optional.of(ZenBoxDataSerializer.getSerializer().parse(reader));
        }

        Signature25519 proof = Signature25519Serializer.getSerializer().parse(reader);
        int forgerIndex = Checker.readIntNotLessThanZero(reader, "forger index");
        Checker.bufferShouldBeEmpty(reader.remaining());

        return new OpenStakeTransaction(inputsId, output, proof, forgerIndex, fee, version);
    }
}
