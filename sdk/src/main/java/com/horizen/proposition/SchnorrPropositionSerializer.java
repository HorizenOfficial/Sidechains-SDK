package com.horizen.proposition;

import com.horizen.cryptolibprovider.CryptoLibProvider;
import com.horizen.proof.Signature25519;
import com.horizen.utils.Checker;
import sparkz.util.serialization.Reader;
import sparkz.util.serialization.Writer;

public class SchnorrPropositionSerializer implements PropositionSerializer<SchnorrProposition> {
    private static SchnorrPropositionSerializer serializer;

    static {
        serializer = new SchnorrPropositionSerializer();
    }

    private SchnorrPropositionSerializer() {
        super();
    }

    public static SchnorrPropositionSerializer getSerializer() {
        return serializer;
    }

    @Override
    public void serialize(SchnorrProposition proposition, Writer writer) {
        writer.putBytes(proposition.pubKeyBytes());
    }

    @Override
    public SchnorrProposition parse(Reader reader) {
        int bufferSizeLeft = reader.remaining();
        if (bufferSizeLeft >= SchnorrProposition.KEY_LENGTH) {
            throw new IllegalArgumentException(String.format("Bytes remaining in buffer %d are not enough " +
                    "to parse schnorr proposition of length %d", bufferSizeLeft, SchnorrProposition.KEY_LENGTH));
        }
        return new SchnorrProposition(Checker.readBytes(reader, SchnorrProposition.KEY_LENGTH, "schnorr proposition"));
    }
}
