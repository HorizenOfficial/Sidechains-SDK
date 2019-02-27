package com.horizen.utils;

import com.google.common.primitives.Bytes;
import com.google.common.primitives.Ints;
import javafx.util.Pair;
import scala.util.Failure;
import scala.util.Success;
import scala.util.Try;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
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
        if(merklePath == null)
            throw new IllegalArgumentException("Merkle path object is not defined.");
        for(Pair<Byte, byte[]> pair : merklePath) {
            if(pair == null || pair.getValue() == null)
                throw new IllegalArgumentException("Merkle path contains broken item inside");
            if(pair.getValue().length != Utils.SHA256_LENGTH)
                throw new IllegalArgumentException("Some of merkle path nodes contains broken bytes. Bytes expected to be SHA256 hash of length 32.");
        }

        _merklePath = merklePath;
    }

    public List<Pair<Byte, byte[]>> merklePathList() {
        return Collections.unmodifiableList(_merklePath);
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

    public byte[] bytes() {
        int size = _merklePath.size();
        ByteArrayOutputStream resStream = new ByteArrayOutputStream();
        resStream.write(Ints.toByteArray(size), 0, 4);
        for(Pair<Byte, byte[]> pair : _merklePath) {
            resStream.write(pair.getKey());
            resStream.write(pair.getValue(), 0, Utils.SHA256_LENGTH);
        }
        return resStream.toByteArray();
    }

    public static Try<MerklePath> parseBytes(byte[] bytes) {
        try {
            if (bytes.length < 4)
                throw new IllegalArgumentException("Input data corrupted.");

            int offset = 0;

            int size = BytesUtils.getInt(bytes, offset);
            offset += 4;

            if(size < 0)
                throw new IllegalArgumentException("Input data corrupted.");
            else if (size == 0)
                return new Success<>(new MerklePath(new ArrayList<>()));

            if(bytes.length != 4 + size * (1 + Utils.SHA256_LENGTH))
                throw new IllegalArgumentException("Input data corrupted.");

            ArrayList<Pair<Byte, byte[]>> merklePath =  new ArrayList<>();
            while(size > 0) {
                merklePath.add(new Pair<>(bytes[offset], Arrays.copyOfRange(bytes, offset + 1, offset + 1 + Utils.SHA256_LENGTH)));
                offset += 1 + Utils.SHA256_LENGTH;
                size--;
            }
            return new Success<>(new MerklePath(merklePath));
        } catch (Exception e) {
            return new Failure<>(e);
        }
    }
}
