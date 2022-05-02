package com.horizen.proposition;

import com.horizen.cryptolibprovider.CryptoLibProvider;
import scorex.util.serialization.Reader;
import scorex.util.serialization.Writer;

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
        return new VrfPublicKey(reader.getBytes(VrfPublicKey.KEY_LENGTH));
    }
}
