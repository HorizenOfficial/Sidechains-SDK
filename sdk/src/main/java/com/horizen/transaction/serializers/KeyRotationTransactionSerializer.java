package com.horizen.transaction.serializers;

import com.horizen.box.data.ZenBoxData;
import com.horizen.box.data.ZenBoxDataSerializer;
import com.horizen.certificatesubmitter.keys.KeyRotationProof;
import com.horizen.certificatesubmitter.keys.KeyRotationProofSerializer;
import com.horizen.proof.*;
import com.horizen.transaction.KeyRotationTransaction;
import com.horizen.transaction.TransactionSerializer;
import scorex.util.serialization.Reader;
import scorex.util.serialization.Writer;
import sparkz.core.NodeViewModifier$;

import java.util.Optional;

import static com.horizen.transaction.KeyRotationTransaction.KEY_ROTATION_TRANSACTION_VERSION;

public class KeyRotationTransactionSerializer implements TransactionSerializer<KeyRotationTransaction>
{
    private static final KeyRotationTransactionSerializer serializer;

    static {
        serializer = new KeyRotationTransactionSerializer();
    }

    private KeyRotationTransactionSerializer() {
        super();
    }

    public static KeyRotationTransactionSerializer getSerializer() {
        return serializer;
    }

    @Override
    public void serialize(KeyRotationTransaction transaction, Writer writer) {
        writer.putBytes(transaction.getInputId());
        writer.put(transaction.version());
        writer.putLong(transaction.fee());
        if(transaction.getOutputBox().isPresent()) {
            writer.putInt(1);
            ZenBoxDataSerializer.getSerializer().serialize(transaction.getOutputBox().get(), writer);
        } else {
            writer.putInt(0);
        }
        Signature25519Serializer.getSerializer().serialize(transaction.getProof(), writer);

        KeyRotationProofSerializer.serialize(transaction.getKeyRotationProof(), writer);
        SchnorrSignatureSerializer.getSerializer().serialize(transaction.getNewKeySignature(), writer);
    }

    @Override
    public KeyRotationTransaction parse(Reader reader) {
        byte[] inputsId = reader.getBytes(NodeViewModifier$.MODULE$.ModifierIdSize());

        byte version = reader.getByte();
        if (version != KEY_ROTATION_TRANSACTION_VERSION) {
            throw new IllegalArgumentException(String.format("Unsupported transaction version[%d].", version));
        }

        long fee = reader.getLong();

        int outputDataIsPresent = reader.getInt();
        Optional<ZenBoxData> output = Optional.empty();
        if (outputDataIsPresent == 1) {
            output = Optional.of(ZenBoxDataSerializer.getSerializer().parse(reader));
        }

        Signature25519 proof = Signature25519Serializer.getSerializer().parse(reader);

        KeyRotationProof keyRotationProof = KeyRotationProofSerializer.parse(reader);

        SchnorrProof newKeySignature = SchnorrSignatureSerializer.getSerializer().parse(reader);

        return new KeyRotationTransaction(inputsId, output, proof, fee, version, keyRotationProof, newKeySignature);
    }
}
