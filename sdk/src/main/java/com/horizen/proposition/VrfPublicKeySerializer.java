package com.horizen.proposition;

import com.horizen.proof.Signature25519;
import com.horizen.utils.Checker;
import sparkz.util.serialization.Reader;
import sparkz.util.serialization.Writer;

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
        byte[] vrfPublicKey = Checker.readBytes(reader, VrfPublicKey.KEY_LENGTH, "VRF public key");
        return new VrfPublicKey(vrfPublicKey);
    }
}
