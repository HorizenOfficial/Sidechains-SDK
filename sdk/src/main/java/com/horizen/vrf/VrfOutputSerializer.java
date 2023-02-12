package com.horizen.vrf;

import com.horizen.utils.Checker;
import sparkz.core.serialization.SparkzSerializer;
import sparkz.util.serialization.Reader;
import sparkz.util.serialization.Writer;

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
        byte[] vrfOutput = Checker.readBytes(reader, VrfOutput.OUTPUT_LENGTH, "Vrf Output");
        return new VrfOutput(vrfOutput);
    }
}
