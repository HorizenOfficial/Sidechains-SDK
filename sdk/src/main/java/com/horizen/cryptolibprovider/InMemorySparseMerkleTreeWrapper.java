package com.horizen.cryptolibprovider;

import com.google.common.collect.*;
import com.horizen.librustsidechains.FieldElement;
import com.horizen.merkletreenative.InMemorySparseMerkleTree;
import com.horizen.merkletreenative.MerklePath;

import java.io.Closeable;
import java.io.IOException;
import java.util.*;

public class InMemorySparseMerkleTreeWrapper implements Closeable {
    private final InMemorySparseMerkleTree merkleTree;
    private final long leavesNumber;
    private final RangeSet<Long> emptyLeaves;

    public InMemorySparseMerkleTreeWrapper(int height) {
        merkleTree = InMemorySparseMerkleTree.init(height);
        leavesNumber = 1L << height;
        emptyLeaves = TreeRangeSet.create(Arrays.asList(Range.closedOpen(0L, leavesNumber)));
    }

    // Returns up to N leftmost empty positions in the tree.
    // Returns less than N if there are not enough empty positions.
    public List<Long> leftmostEmptyPositions(long atMost) {
        if(atMost <= 0)
            return new ArrayList<>();
        List<Long> emptyPositions = new ArrayList<>();

        for(Range<Long> range : emptyLeaves.asRanges()) {
            for(long emptyPos: ContiguousSet.create(range, DiscreteDomain.longs())) {
                emptyPositions.add(emptyPos);
                if(emptyPositions.size() == atMost)
                    return emptyPositions;
            }
        }
        return emptyPositions;
    }

    // Check the leaf is empty on given position.
    public boolean isLeafEmpty(long pos) {
        try {
            return merkleTree.isPositionEmpty(pos);
        } catch (Exception e) {
            // position is out of tree leaves range.
            return false;
        }
    }

    // Check max leaves number.
    public long leavesNumber() {
        return leavesNumber;
    }

    // Returns false if one of the positions was occupied before.
    public boolean addLeaves(Map<Long, FieldElement> leaves) {
        // check that all leaves refer to empty positions in the merkle tree.
        for(Long pos : leaves.keySet()) {
            if(!emptyLeaves.contains(pos))
                return false;
        }
        // try to update merkle tree
        try {
            merkleTree.addLeaves(leaves);
        } catch (Exception e) {
            return false;
        }

        for(Long pos : leaves.keySet()) {
            emptyLeaves.remove(Range.singleton(pos));
        }
        return true;
    }

    // Remove the leaves at given positions.
    // Returns false if one of the positions is out of tree bounds or leaf is already empty.
    public boolean removeLeaves(long[] positions) {
        Set<Long> positionSet = new HashSet<>();
        // check positions range
        for(long pos: positions) {
            if(pos < 0 || pos >= leavesNumber() || isLeafEmpty(pos))
                return false;
            else
                positionSet.add(pos);
        }

        // update merkle tree
        try {
            merkleTree.removeLeaves(positionSet);
        } catch (Exception e) {
            return false;
        }

        for(long pos: positions) {
            emptyLeaves.add(Range.closedOpen(pos, pos + 1));
        }

        return true;
    }

    // Returns the root of the merkle tree or null if tree state was not finalized
    public byte[] calculateRoot() {
        try {
            merkleTree.finalizeInPlace();
            FieldElement root = merkleTree.root();
            byte[] rootBytes = root.serializeFieldElement();
            root.freeFieldElement();
            return rootBytes;
        } catch (Exception e) {
            return null;
        }
    }

    // Return byte representation of merkle path or null if there is no leaf at given pos.
    public byte[] merklePath(long pos) {
        try {
            merkleTree.finalizeInPlace();
            MerklePath mp = merkleTree.getMerklePath(pos);
            byte[] mpBytes = mp.serialize();
            mp.freeMerklePath();
            return mpBytes;
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public void close() throws IOException {
        merkleTree.freeInMemorySparseMerkleTree();
    }
}
