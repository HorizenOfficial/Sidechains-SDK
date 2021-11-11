package com.horizen.cryptolibprovider;

import com.google.common.collect.*;
import com.google.common.primitives.Ints;
import com.horizen.librustsidechains.FieldElement;
import com.horizen.utils.Pair;
import scorex.crypto.hash.Blake2b256;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

// temporary structure
class DummySparseMerkleTree {
    // dummy structure: leaves[i] == o -> empty, otherwise - occupied.
    private byte[] leaves;

    public DummySparseMerkleTree(int height) {
        leaves = new byte[1 << height];
    }

    public int leavesNumber() {
        return leaves.length;
    }

    public boolean isLeafEmpty(int pos) {
        return leaves[pos] == 0;
    }

    public boolean addLeaves(List<Pair<FieldElement, Integer>> leavesToAppend) {
        for(Pair<FieldElement, Integer> leaf : leavesToAppend) {
            leaves[leaf.getValue()] = 1;
        }
        return true;
    }

    public boolean removeLeaves(List<Integer> leavesToRemove) {
        for(Integer pos : leavesToRemove) {
            leaves[pos] = 0;
        }
        return true;
    }

    public byte[] root(){
        return (byte[])Blake2b256.hash(Ints.toByteArray(Arrays.hashCode(leaves)));
    }

    public byte[] merklePath(int pos) {
        if(!isLeafEmpty(pos))
            return Ints.toByteArray(pos);
        return null;
    }
}

public class InMemorySparseMerkleTreeWrapper implements Closeable {
    private DummySparseMerkleTree merkleTree;
    private RangeSet<Integer> emptyLeaves;

    public InMemorySparseMerkleTreeWrapper(int height) {
        merkleTree = new DummySparseMerkleTree(height);
        emptyLeaves = TreeRangeSet.create(Arrays.asList(Range.closedOpen(0, 1 << height)));
    }

    // returns N leftmost empty positions in the tree
    // or null if there are not enough empty positions
    public List<Integer> leftmostEmptyPositions(int count) {
        if(count <= 0)
            return null;
        List<Integer> emptyPositions = new ArrayList<>();

        for(Range<Integer> range : emptyLeaves.asRanges()) {
            for(int emptyPos: ContiguousSet.create(range, DiscreteDomain.integers())) {
                emptyPositions.add(emptyPos);
                if(emptyPositions.size() == count)
                    return emptyPositions;
            }
        }
        return null;
    }

    // Check position
    public boolean isLeafEmpty(int pos) {
        return merkleTree.isLeafEmpty(pos);
    }

    // check max leaves number
    public int leavesNumber() {
        return merkleTree.leavesNumber();
    }

    // returns false if leaf is not a FE, or pos was occupied before
    boolean addLeaves(List<Pair<FieldElement, Integer>> leaves) {
        // check that all leaves refer to empty positions in the merkle tree.
        for(Pair<FieldElement, Integer> leaf : leaves) {
            if(!emptyLeaves.contains(leaf.getValue()))
                return false;
        }
        // update merkle tree
        if(!merkleTree.addLeaves(leaves))
            return false;

        for(Pair<FieldElement, Integer> leaf : leaves) {
            emptyLeaves.remove(Range.singleton(leaf.getValue()));
        }
        return true;
    }

    // remove the leaves at given positions if were present
    boolean removeLeaves(List<Integer> positions) {
        // check positions range
        for(int pos: positions) {
            if(pos < 0 || pos >= leavesNumber())
                return false;
        }

        // update merkle tree
        if(!merkleTree.removeLeaves(positions))
            return false;

        for(int pos: positions) {
            emptyLeaves.add(Range.singleton(pos));
        }

        return true;
    }

    // returns the root of the merkle tree
    byte[] calculateRoot() {
        return merkleTree.root();
    }

    // return byte representation of merkle path or null if there is no leaf at given pos.
    byte[] merklePath(int pos) {
        return merkleTree.merklePath(pos);
    }

    @Override
    public void close() throws IOException {
        // free native merkle tree pointer
    }
}
