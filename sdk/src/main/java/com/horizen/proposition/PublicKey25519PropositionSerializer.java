package com.horizen.proposition;

import sparkz.util.serialization.Reader;
import sparkz.util.serialization.Writer;
import sparkz.util.serialization.VLQByteBufferReader;
import java.nio.ByteBuffer;

public final class PublicKey25519PropositionSerializer implements PropositionSerializer<PublicKey25519Proposition> {
    private static PublicKey25519PropositionSerializer serializer;

    static {
        serializer = new PublicKey25519PropositionSerializer();
    }

    private PublicKey25519PropositionSerializer() {
        super();
    }

    public static PublicKey25519PropositionSerializer getSerializer() {
        return serializer;
    }

    @Override
    public void serialize(PublicKey25519Proposition proposition, Writer writer) {
        writer.putBytes(proposition.pubKeyBytes());
    }

    @Override
    public PublicKey25519Proposition parse(Reader reader) {
        return parse(reader, false);
    }

    public PublicKey25519Proposition parse(Reader reader, boolean checkPubKey) {
        return new PublicKey25519Proposition(reader.getBytes(PublicKey25519Proposition.KEY_LENGTH), checkPubKey);
    }

    public PublicKey25519Proposition parseBytesAndCheck(byte[] propositionBytes) {
        VLQByteBufferReader bufferReader = new VLQByteBufferReader(ByteBuffer.wrap(propositionBytes));
        return parse(bufferReader, true);
    }
}
