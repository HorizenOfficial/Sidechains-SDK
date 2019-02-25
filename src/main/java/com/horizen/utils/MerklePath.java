package com.horizen.utils;

import javafx.util.Pair;

import java.util.List;

public class MerklePath {
    List<Pair<Byte, byte[]>> _merklePath;

    // Merkle Tree:
    //
    //             root
    //             / \
    //            /   \
    //          A      B
    //         / \    / \
    //        t1 t2  t3 t4
    //
    // Leaf t2 has index 1 (leaves indexation starts from 0)
    // Merkle path for t2 is [(0, t1), (1, B)], where:
    // 0 means, that we need to concatenate pair value left to current one
    // 1 means, that we need to concatenate pair value right to current one

    public MerklePath(List<Pair<Byte, byte[]>> merklePath) {
        _merklePath = merklePath;
    }

    // apply merkle path to element and return resulting hash, which must be a Merkle Root hash of corresponding MerkleTree.
    public byte[] apply(byte[] leaf) {
        byte[] tmp = leaf;
        for(Pair<Byte, byte[]> node : _merklePath) {
            if(node.getKey() == (byte)0) // concatenate current node value LEFT to tmp
                tmp = BytesUtils.reverseBytes(Utils.doubleSHA256HashOfConcatenation(BytesUtils.reverseBytes(node.getValue()), BytesUtils.reverseBytes(tmp)));
            else // concatenate current node value RIGHT to tmp
                tmp = BytesUtils.reverseBytes(Utils.doubleSHA256HashOfConcatenation(BytesUtils.reverseBytes(tmp), BytesUtils.reverseBytes(node.getValue())));
        }
        return tmp;
    }
}
