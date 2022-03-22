package com.horizen.utils;

import com.horizen.utils.Pair;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class MerkleTree
{

    public static int ROOT_HASH_LENGTH = 32;

    private int _leavesNumber;
    private List<byte[]> _merkleTree;
    private boolean _isMutated;

    private MerkleTree(int leavesNumber, List<byte[]> merkleTree, boolean isMutated) {
        _leavesNumber = leavesNumber;
        _merkleTree = merkleTree;
        _isMutated = isMutated;
    }

    /* Note: Description is taken from ZenCore CBlock::BuildMerkleTree method.

       WARNING! If you're reading this because you're learning about crypto
       and/or designing a new system that will use merkle trees, keep in mind
       that the following merkle tree algorithm has a serious flaw related to
       duplicate txids, resulting in a vulnerability (CVE-2012-2459).
       The reason is that if the number of hashes in the list at a given time
       is odd, the last one is duplicated before computing the next level (which
       is unusual in Merkle trees). This results in certain sequences of
       transactions leading to the same merkle root. For example, these two
       trees:
                    A               A
                  /  \            /   \
                B     C         B       C
               / \    |        / \     / \
              D   E   F       D   E   F   F
             / \ / \ / \     / \ / \ / \ / \
             1 2 3 4 5 6     1 2 3 4 5 6 5 6
       for transaction lists [1,2,3,4,5,6] and [1,2,3,4,5,6,5,6] (where 5 and
       6 are repeated) result in the same root hash A (because the hash of both
       of (F) and (F,F) is C).
       The vulnerability results from being able to send a block with such a
       transaction list, with the same merkle root, and the same block hash as
       the original without duplication, resulting in failed validation. If the
       receiving node proceeds to mark that block as permanently invalid
       however, it will fail to accept further unmodified (and thus potentially
       valid) versions of the same block. We defend against this by detecting
       the case where we would hash two identical hashes at the end of the list
       together, and treating that identically to the block having an invalid
       merkle root. Assuming no double-SHA256 collisions, this will detect all
       known ways of changing the transactions without affecting the merkle
       root.
    */
    public static MerkleTree createMerkleTree(List<byte[]> leavesHashes) {
        if(leavesHashes == null || leavesHashes.size() == 0)
            throw new IllegalArgumentException("Non leaves provided. Merkle Tree can not be calculated.");

        ArrayList<byte[]> merkleTree = new ArrayList<>(leavesHashes);

        // Go through level nodes, calculate hashes for next level until get the root.
        // offset in a merkleTree list.
        int offset = 0;
        // number of nodes on current level.
        int levelSize = merkleTree.size();

        // The flag that detect if we have duplicate at the end of some level.
        boolean isMutated = false;

        // Note: Root level size is 1
        while(levelSize > 1) {
            for (int left = 0; left < levelSize; left += 2) {
                // Right can be the same as left if we have an odd number of nodes on the level.
                int right = Math.min(left + 1, levelSize - 1);
                byte[] leftBytes = BytesUtils.reverseBytes(merkleTree.get(offset + left));
                byte[] rightBytes = BytesUtils.reverseBytes(merkleTree.get(offset + right));

                if(right == left + 1 && right + 1 == levelSize && Arrays.equals(leftBytes, rightBytes)) {
                    // Two identical hashes at the end of the list at a particular level.
                    isMutated = true;
                }
                merkleTree.add(BytesUtils.reverseBytes(Utils.doubleSHA256HashOfConcatenation(leftBytes, rightBytes)));
            }
            offset += levelSize;
            // calculate next level size
            levelSize = (levelSize + 1) / 2;
        }

        return new MerkleTree(leavesHashes.size(), merkleTree, isMutated);
    }

    public byte[] rootHash() {
        return _merkleTree.get(_merkleTree.size() - 1);
    }

    public List<byte[]> toList() {
        return _merkleTree;
    }

    public int leavesNumber() {
        return _leavesNumber;
    }

    public List<byte[]> leaves() {
        return _merkleTree.subList(0, _leavesNumber);
    }

    public MerklePath getMerklePathForLeaf(int leafIdx) {
        if(leafIdx < 0 || leafIdx >= _leavesNumber)
            throw new IllegalArgumentException("Leaf index is out of bound. Merkle Path can not be calculated.");

        // offset in a merkleTree list.
        int offset = 0;
        // number of nodes on current level.
        int levelSize = _leavesNumber;
        int idxOnLevel = leafIdx;


        // pair of <concatenation position> : <hash to concatenate>
        ArrayList<Pair<Byte, byte[]>> merklePath = new ArrayList<>();

        while(levelSize > 1) {
            boolean isOdd = levelSize % 2 == 1;
            if(isOdd && idxOnLevel == levelSize - 1) // last element on level with odd number of elements -> concatenate with itself
                merklePath.add(new Pair<>((byte)1, _merkleTree.get(offset + idxOnLevel)));
            else if(idxOnLevel % 2 == 1) // right child
                merklePath.add(new Pair<>((byte)0, _merkleTree.get(offset + idxOnLevel - 1)));
            else // left child
                merklePath.add(new Pair<>((byte)1, _merkleTree.get(offset + idxOnLevel + 1)));

            offset += levelSize;
            // calculate next level size
            levelSize = (levelSize + 1) / 2;
            // calculate next level idx
            idxOnLevel /= 2;
        }
        return new MerklePath(merklePath);
    }

    public boolean validateMerklePath(byte[] leaf, MerklePath merklePath) {
        return Arrays.equals(rootHash(), merklePath.apply(leaf));
    }

    public boolean isMutated() {
        return _isMutated;
    }
}
