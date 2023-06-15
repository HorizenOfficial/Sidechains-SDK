package io.horizen.proposition;

import sparkz.util.serialization.Reader;
import sparkz.util.serialization.VLQByteBufferReader;
import sparkz.util.serialization.Writer;

import java.nio.ByteBuffer;

public class VrfPublicKeySerializer implements PropositionSerializer<VrfPublicKey> {
    private static VrfPublicKeySerializer serializer;

    static {
        serializer = new VrfPublicKeySerializer();
    }

    private VrfPublicKeySerializer() {
        super();
    }

    public static VrfPublicKeySerializer getSerializer() {
        return serializer;
    }

    @Override
    public void serialize(VrfPublicKey proposition, Writer writer) {
        writer.putBytes(proposition.pubKeyBytes());
    }

    @Override
    public VrfPublicKey parse(Reader reader) {
        return parse(reader, false);
    }

    public VrfPublicKey parse(Reader reader, boolean checkPublicKey) {
        VrfPublicKey publicKey = new VrfPublicKey(reader.getBytes(VrfPublicKey.KEY_LENGTH), checkPublicKey);

        return publicKey;
    }

    public VrfPublicKey parseBytesAndCheck(byte[] propositionBytes) {
        VLQByteBufferReader bufferReader = new VLQByteBufferReader(ByteBuffer.wrap(propositionBytes));
        return parse(bufferReader, true);
    }
}
