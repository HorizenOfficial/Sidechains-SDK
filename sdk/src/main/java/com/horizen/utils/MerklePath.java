package com.horizen.utils;

import scorex.core.serialization.ScorexSerializer;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class MerklePath implements scorex.core.serialization.BytesSerializable {
    List<Pair<Byte, byte[]>> merklePath;

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
        if(merklePath == null)
            throw new IllegalArgumentException("Merkle path object is not defined.");
        for(Pair<Byte, byte[]> pair : merklePath) {
            if(pair == null || pair.getValue() == null)
                throw new IllegalArgumentException("Merkle path contains broken item inside");
            if(pair.getValue().length != Utils.SHA256_LENGTH)
                throw new IllegalArgumentException("Some of merkle path nodes contains broken bytes. Bytes expected to be SHA256 hash of length 32.");
        }

        this.merklePath = merklePath;
    }

    public List<Pair<Byte, byte[]>> merklePathList() {
        return Collections.unmodifiableList(merklePath);
    }

    // apply merkle path to element and return resulting hash, which must be a Merkle Root hash of corresponding MerkleTree.
    public byte[] apply(byte[] leaf) {
        byte[] tmp = leaf;
        for(Pair<Byte, byte[]> node : merklePath) {
            if(node.getKey() == (byte)0) // concatenate current node value LEFT to tmp
                tmp = BytesUtils.reverseBytes(Utils.doubleSHA256HashOfConcatenation(BytesUtils.reverseBytes(node.getValue()), BytesUtils.reverseBytes(tmp)));
            else // concatenate current node value RIGHT to tmp
                tmp = BytesUtils.reverseBytes(Utils.doubleSHA256HashOfConcatenation(BytesUtils.reverseBytes(tmp), BytesUtils.reverseBytes(node.getValue())));
        }
        return tmp;
    }

    @Override
    public byte[] bytes() {
        return serializer().toBytes(this);
    }

    @Override
    public ScorexSerializer serializer() {
        return MerklePathSerializer.getSerializer();
    }

    public boolean isLeftmost() {
        for(Pair<Byte, byte[]> neighbour: merklePath) {
            if (neighbour.getKey() != 1)
                return false;
        }
        return true;
    }

    public boolean isRightmost(byte[] leaf) {
        byte[] currentNode = leaf;
        for(Pair<Byte, byte[]> neighbour : merklePath) {
            if(neighbour.getKey() == (byte)0) {
                // left neighbour
                // concatenate neighbour value LEFT to currentNode
                currentNode = BytesUtils.reverseBytes(Utils.doubleSHA256HashOfConcatenation(BytesUtils.reverseBytes(neighbour.getValue()), BytesUtils.reverseBytes(currentNode)));
            } else {
                // right neighbour
                // if not equal, merkle path doesn't lead to the rightmost leaf.
                if(!Arrays.equals(currentNode, neighbour.getValue()))
                    return false;
                // concatenate neighbour value RIGHT to currentNode
                currentNode = BytesUtils.reverseBytes(Utils.doubleSHA256HashOfConcatenation(BytesUtils.reverseBytes(currentNode), BytesUtils.reverseBytes(neighbour.getValue())));
            }
        }
        return true;
    }
    public int leafIndex() {
        int index = 0;
        for (int i = merklePath.size() - 1; i >= 0; i--) {
            index = index << 1;
            if (merklePath.get(i).getKey() == 0)
                index ++;
        }
        return index;
    }

    @Override
    public int hashCode() {
        int result = 1;
        for(Pair<Byte, byte[]> pair : merklePath) {
            result += 31 * result + pair.getKey().intValue() + Arrays.hashCode(pair.getValue());
        }
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null)
            return false;
        if (!(this.getClass().equals(obj.getClass())))
            return false;
        if (obj == this)
            return true;
        MerklePath merklePath = (MerklePath) obj;

        if(this.merklePath.size() != merklePath.merklePath.size())
            return false;
        for(int i = 0; i < this.merklePath.size(); i++) {
            Pair<Byte, byte[]> pair = this.merklePath.get(i);
            Pair<Byte, byte[]> otherPair = merklePath.merklePath.get(i);
            if(!pair.getKey().equals(otherPair.getKey()) || !Arrays.equals(pair.getValue(), otherPair.getValue()))
                return false;
        }
        return true;
    }
}
