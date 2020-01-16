package com.horizen.vrf;

import scorex.core.serialization.ScorexSerializer;
import scorex.util.serialization.Reader;
import scorex.util.serialization.Writer;

public class VRFSecretKeySerializer
    implements ScorexSerializer<VRFSecretKey>
{

    private static VRFPublicKeySerializer serializer;

    static {
        serializer = new VRFPublicKeySerializer();
    }

    public static VRFPublicKeySerializer getSerializer() {
        return serializer;
    }

    @Override
    public void serialize(VRFSecretKey key, Writer writer) {
        writer.putBytes(key.bytes());
    }

    @Override
    public VRFSecretKey parse(Reader reader) {
        return VRFSecretKey.parseBytes(reader.getBytes(reader.remaining()));
    }
}
