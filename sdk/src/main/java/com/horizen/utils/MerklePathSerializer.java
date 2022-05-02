package com.horizen.utils;

import scorex.core.serialization.ScorexSerializer;
import scorex.util.serialization.Reader;
import scorex.util.serialization.Writer;
import java.util.ArrayList;

public class MerklePathSerializer implements ScorexSerializer<MerklePath> {
    private static MerklePathSerializer serializer;

    static {
        serializer = new MerklePathSerializer();
    }

    private MerklePathSerializer() {}

    public static MerklePathSerializer getSerializer() {
        return serializer;
    }

    @Override
    public void serialize(MerklePath merklePath, Writer writer) {
        int size = merklePath.merklePath.size();
        writer.putInt(size);
        for(Pair<Byte, byte[]> pair : merklePath.merklePath) {
            writer.put(pair.getKey());
            writer.putBytes(pair.getValue());
        }
    }

    @Override
    public MerklePath parse(Reader reader) {
        int size = reader.getInt();

        if(size < 0)
            throw new IllegalArgumentException("Input data corrupted.");
        else if (size == 0)
            return new MerklePath(new ArrayList<>());

        ArrayList<Pair<Byte, byte[]>> merklePath = new ArrayList<>();
        while(size > 0) {
            byte key = reader.getByte();
            byte[] value = reader.getBytes(Utils.SHA256_LENGTH);
            merklePath.add(new Pair<>(key, value));
            size--;
        }

        return new MerklePath(merklePath);
    }
}
