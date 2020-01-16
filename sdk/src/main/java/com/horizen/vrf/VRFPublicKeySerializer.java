package com.horizen.vrf;

import scorex.core.serialization.ScorexSerializer;
import scorex.util.serialization.Reader;
import scorex.util.serialization.Writer;

public class VRFPublicKeySerializer
    implements ScorexSerializer<VRFPublicKey>
{

    private static VRFPublicKeySerializer serializer;

    static {
        serializer = new VRFPublicKeySerializer();
    }

    public static VRFPublicKeySerializer getSerializer() {
        return serializer;
    }

    @Override
    public void serialize(VRFPublicKey key, Writer writer) {
        writer.putBytes(key.bytes());
    }

    @Override
    public VRFPublicKey parse(Reader reader) {
        return VRFPublicKey.parseBytes(reader.getBytes(reader.remaining()));
    }
}
