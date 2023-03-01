package io.horizen.proposition;

import sparkz.util.serialization.Reader;
import sparkz.util.serialization.Writer;
import sparkz.util.serialization.VLQByteBufferReader;
import java.nio.ByteBuffer;

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
        return parse(reader, false);
    }

    public SchnorrProposition parse(Reader reader, boolean checkPublicKey) {
        return new SchnorrProposition(reader.getBytes(SchnorrProposition.KEY_LENGTH), checkPublicKey);
    }

    public SchnorrProposition parseBytesAndCheck(byte[] propositionBytes) {
        VLQByteBufferReader bufferReader = new VLQByteBufferReader(ByteBuffer.wrap(propositionBytes));
        return parse(bufferReader, true);
    }
}
