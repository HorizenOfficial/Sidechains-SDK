package com.horizen.utils;

import java.util.ArrayList;
import java.util.List;

public final class MerkleTree
{
    private MerkleTree() {}

    // The Merkle root is based on a tree of hashes calculated from the transactions:
    //
    //             root
    //             / \
    //            /   \
    //          A      B
    //         / \    / \
    //        t1 t2  t3 t4
    //
    // The tree is represented as a list [t1, t2, t3, t4, A, B, root] where each entry is a hash.
    //
    // The hashing algorithm is double SHA-256.
    // The leaves are a hash of the serialized contents of the transaction.
    // The interior nodes are hashes of the concatenation of the two child hashes.


    // Note: if the number of leaves is not a power of two, then to calculate merkle tree the last leave is repeated to make it so.
    // A tree with 5 leaves would look like this:
    //
    //                    root
    //                  /      \
    //                /          \
    //              AB            CD
    //            /  \          /   \
    //          A     B       C      D
    //         / \   / \    /  \   /  \
    //       t1 t2  t3 t4  t5 t5  t5  t5
    //
    // For optimisation purpose, we don't need to calculate branch D to calculate CD, because C == D, so CD = hash(C+C)
    public static List<byte[]> calculateMerkleTree(List<byte[]> leavesHashes) {
        if(leavesHashes == null || leavesHashes.size() == 0)
            throw new IllegalArgumentException("Non leaves provided. Merkle Tree can not be calculated.");

        ArrayList<byte[]> merkleTree = new ArrayList<>(leavesHashes);

        // Go through level nodes, calculate hashes for next level until get the root.
        // offset in a merkleTree list.
        int offset = 0;
        // number of nodes on current level.
        int levelSize = merkleTree.size();

        // Note: Root level size is 1
        while(levelSize > 1) {
            for (int left = 0; left < levelSize; left += 2) {
                // Right can be the same as left if we have an odd number of nodes on the level.
                int right = Math.min(left + 1, levelSize - 1);
                byte[] leftBytes = BytesUtils.reverseBytes(merkleTree.get(offset + left));
                byte[] rightBytes = BytesUtils.reverseBytes(merkleTree.get(offset + right));
                merkleTree.add(BytesUtils.reverseBytes(Utils.doubleSHA256HashOfConcatenation(leftBytes, rightBytes)));
            }
            offset += levelSize;
            // calculate next level size
            levelSize = (levelSize + 1) / 2;
        }

        return merkleTree;
    }
}
