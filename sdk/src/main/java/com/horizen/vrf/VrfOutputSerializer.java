package com.horizen.vrf;

import sparkz.core.serialization.SparkzSerializer;
import scorex.util.serialization.Reader;
import scorex.util.serialization.Writer;

public class VrfOutputSerializer implements SparkzSerializer<VrfOutput> {
    private static VrfOutputSerializer serializer;

    static {
        serializer = new VrfOutputSerializer();
    }

    private VrfOutputSerializer() {
        super();
    }

    public static VrfOutputSerializer getSerializer() {
        return serializer;
    }

    @Override
    public void serialize(VrfOutput vrfOutput, Writer writer) {
        writer.putBytes(vrfOutput.vrfOutputBytes);
    }

    @Override
    public VrfOutput parse(Reader reader) {
        return new VrfOutput(reader.getBytes(VrfOutput.OUTPUT_LENGTH));
    }
}
