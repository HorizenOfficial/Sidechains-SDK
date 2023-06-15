package io.horizen.utxo.transaction;

import io.horizen.certificatesubmitter.keys.KeyRotationProof;
import io.horizen.certificatesubmitter.keys.KeyRotationProofSerializer;
import io.horizen.proof.SchnorrProof;
import io.horizen.proof.SchnorrSignatureSerializer;
import io.horizen.proof.Signature25519;
import io.horizen.proof.Signature25519Serializer;
import io.horizen.transaction.TransactionSerializer;
import io.horizen.utxo.box.data.ZenBoxData;
import io.horizen.utxo.box.data.ZenBoxDataSerializer;
import sparkz.core.NodeViewModifier$;
import sparkz.util.serialization.Reader;
import sparkz.util.serialization.Writer;

import java.util.Optional;

import static io.horizen.utxo.transaction.CertificateKeyRotationTransaction.CERTIFICATE_KEY_ROTATION_TRANSACTION_VERSION;

public class CertificateKeyRotationTransactionSerializer implements TransactionSerializer<CertificateKeyRotationTransaction>
{
    private static final CertificateKeyRotationTransactionSerializer serializer;

    static {
        serializer = new CertificateKeyRotationTransactionSerializer();
    }

    private CertificateKeyRotationTransactionSerializer() {
        super();
    }

    public static CertificateKeyRotationTransactionSerializer getSerializer() {
        return serializer;
    }

    @Override
    public void serialize(CertificateKeyRotationTransaction transaction, Writer writer) {
        writer.put(transaction.version());
        writer.putLong(transaction.fee());
        writer.putBytes(transaction.inputId);
        Signature25519Serializer.getSerializer().serialize(transaction.proof, writer);
        if(transaction.outputData.isPresent()) {
            writer.putInt(1);
            ZenBoxDataSerializer.getSerializer().serialize(transaction.outputData.get(), writer);
        } else {
            writer.putInt(0);
        }

        KeyRotationProofSerializer.serialize(transaction.keyRotationProof, writer);
        SchnorrSignatureSerializer.getSerializer().serialize(transaction.getNewKeySignature(), writer);
    }

    @Override
    public CertificateKeyRotationTransaction parse(Reader reader) {

        byte version = reader.getByte();
        if (version != CERTIFICATE_KEY_ROTATION_TRANSACTION_VERSION) {
            throw new IllegalArgumentException(String.format("Unsupported transaction version[%d].", version));
        }

        long fee = reader.getLong();

        byte[] inputsId = reader.getBytes(NodeViewModifier$.MODULE$.ModifierIdSize());

        Signature25519 proof = Signature25519Serializer.getSerializer().parse(reader);

        int outputDataIsPresent = reader.getInt();
        Optional<ZenBoxData> output = Optional.empty();
        if (outputDataIsPresent == 1) {
            output = Optional.of(ZenBoxDataSerializer.getSerializer().parse(reader));
        }


        KeyRotationProof keyRotationProof = KeyRotationProofSerializer.parse(reader);

        SchnorrProof newKeySignature = SchnorrSignatureSerializer.getSerializer().parse(reader);

        return new CertificateKeyRotationTransaction(inputsId, output, proof, fee, version, keyRotationProof, newKeySignature);
    }
}
