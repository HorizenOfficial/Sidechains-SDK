package com.horizen.fixtures;

import com.horizen.utils.MerklePath;
import com.horizen.utils.MerkleTree;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class MerkleTreeFixture {
    static public byte[] generateNextBytes(int size, int seed) {
        byte[] bytes = new byte[size];
        new Random(seed).nextBytes(bytes);
        return bytes;
    }

    static public MerklePath generateRandomMerklePath(long seed) {
        Random rnd = new Random(seed);
        List<byte[]> merkleLeafs = new ArrayList<byte[]>();
        merkleLeafs.add(generateNextBytes(32, rnd.nextInt()));
        merkleLeafs.add(generateNextBytes(32, rnd.nextInt()));
        MerkleTree merkleTree = MerkleTree.createMerkleTree(merkleLeafs);
        return merkleTree.getMerklePathForLeaf(0);
    }
}
