package com.horizen.cryptolibprovider;

import com.horizen.librustsidechains.FieldElement;
import com.horizen.merkletreenative.PositionLeaf;
import com.horizen.utils.BytesUtils;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import static org.junit.Assert.*;

public class InMemorySparseMerkleTreeWrapperTest {
    final int treeHeight = 10;
    int totalLeavesNumber = 1 << treeHeight;

    @Test
    public void checkEmptyPositions() {
        InMemorySparseMerkleTreeWrapper merkleTreeWrapper = new InMemorySparseMerkleTreeWrapper(treeHeight);

        // Test single leaf empty pos for empty tree
        assertEquals("Different leftmost empty position retrieved for a single leaf.",
                Collections.singletonList(0L), merkleTreeWrapper.leftmostEmptyPositions(1));

        // Test all empty positions for empty tree
        List<Long> allEmptyPositions = merkleTreeWrapper.leftmostEmptyPositions(totalLeavesNumber);
        assertEquals("Different empty positions array size.", totalLeavesNumber, allEmptyPositions.size());
        assertEquals("Different empty position retrieved.", 0, allEmptyPositions.get(0).intValue());
        assertEquals("Different empty position retrieved.", totalLeavesNumber - 1, allEmptyPositions.get(allEmptyPositions.size() - 1).intValue());

        // Test out of range empty positions for empty tree
        assertNull("No positions expected.", merkleTreeWrapper.leftmostEmptyPositions(totalLeavesNumber + 1));
        assertNull("No positions expected.", merkleTreeWrapper.leftmostEmptyPositions(0));
        assertNull("No positions expected.", merkleTreeWrapper.leftmostEmptyPositions(-1));
    }

    @Test
    public void root() {
        InMemorySparseMerkleTreeWrapper merkleTreeWrapper = new InMemorySparseMerkleTreeWrapper(treeHeight);
        byte[] root = merkleTreeWrapper.calculateRoot();
        String rootHex = BytesUtils.toHexString(root);
        assertEquals("Root of empty tree is different.", "cae22c26168c9275bfa5ad7aa496e94450367a19be9a142e2c6a8d3f5afaaf26", rootHex);
    }

    @Test
    public void addLeaves() {
        InMemorySparseMerkleTreeWrapper merkleTreeWrapper = new InMemorySparseMerkleTreeWrapper(treeHeight);

        // Test: append leaves to empty positions
        List<PositionLeaf> leavesToAppend = Arrays.asList(
                new PositionLeaf(0, FieldElement.createRandom(123L)),
                new PositionLeaf(1, FieldElement.createRandom(456L)),
                new PositionLeaf(10, FieldElement.createRandom(789L)),
                new PositionLeaf(15, FieldElement.createRandom(111L)),
                new PositionLeaf(16, FieldElement.createRandom(222L)),
                new PositionLeaf(totalLeavesNumber - 1, FieldElement.createRandom(333L))
        );

        assertTrue("Leaves expected to be added.", merkleTreeWrapper.addLeaves(leavesToAppend));

        for(PositionLeaf leaf : leavesToAppend) {
            long pos = leaf.getPosition();
            assertFalse("Leaf expected to be occupied.", merkleTreeWrapper.isLeafEmpty(pos));
        }

        byte[] root = merkleTreeWrapper.calculateRoot();
        assertNotNull("Root should exist.", root);

        // Test: try to append the same leaves second time.
        assertFalse("Leaves expected to be added.", merkleTreeWrapper.addLeaves(leavesToAppend));

        List<PositionLeaf> sublist = leavesToAppend.subList(0, 2);
        assertFalse("Leaves expected to be added.", merkleTreeWrapper.addLeaves(sublist));


        // Test: check empty positions
        assertEquals("Different leftmost empty position retrieved for a single leaf.",
                Collections.singletonList(2L), merkleTreeWrapper.leftmostEmptyPositions(1));

        List<Long> expectedEmptyPositions =
                LongStream.range(0L, 50L)
                        .filter(pos -> leavesToAppend.stream().noneMatch(leaf -> leaf.getPosition() == pos))
                        .boxed()
                        .collect(Collectors.toList());

        assertEquals("Different leftmost empty position retrieved for multiple leaves.",
                expectedEmptyPositions, merkleTreeWrapper.leftmostEmptyPositions(expectedEmptyPositions.size()));

        // Do cleanup
        leavesToAppend.forEach(leaf -> {
            try {
                leaf.close();
            } catch (Exception e) {
                fail(e.getMessage());
            }
        });
    }

    @Test
    public void removeLeaves() {
        InMemorySparseMerkleTreeWrapper merkleTreeWrapper = new InMemorySparseMerkleTreeWrapper(treeHeight);

        // Test: remove leaves from empty tree -> do nothing
        assertFalse("Leaves expected to be removed", merkleTreeWrapper.removeLeaves(new long[] { 0L, 1L, 2L, 10L }));


        // Test: try to remove leaf out of range
        assertFalse("Failure expected while removing leaf with out of range position.",
                merkleTreeWrapper.removeLeaves(new long[] { totalLeavesNumber }));

        assertFalse("Failure expected while removing leaf with out of range position.",
                merkleTreeWrapper.removeLeaves(new long[] { -1 }));


        // Test: remove existing leaves:
        // Add leaves first
        List<PositionLeaf> leavesToAppend = Arrays.asList(
                new PositionLeaf(1L, FieldElement.createRandom(456L)),
                new PositionLeaf(10L, FieldElement.createRandom(789L)),
                new PositionLeaf(15L, FieldElement.createRandom(111L))
        );
        assertTrue("Leaves expected to be added.", merkleTreeWrapper.addLeaves(leavesToAppend));

        long[] leavesToRemove = new long[leavesToAppend.size()];
        for(int i = 0; i < leavesToAppend.size(); i++)
            leavesToRemove[i] = leavesToAppend.get(i).getPosition();

        assertTrue("Leaves expected to be removed.", merkleTreeWrapper.removeLeaves(leavesToRemove));

        for(long pos : leavesToRemove) {
            assertTrue("Leaf expected to be empty.", merkleTreeWrapper.isLeafEmpty(pos));
        }

        List<Long> expectedEmptyPositions = LongStream.range(0L, 50L).boxed().collect(Collectors.toList());

        assertEquals("Different leftmost empty position retrieved for multiple leaves.",
                expectedEmptyPositions, merkleTreeWrapper.leftmostEmptyPositions(expectedEmptyPositions.size()));

        byte[] root = merkleTreeWrapper.calculateRoot();
        assertNotNull("Root should exist.", root);
    }
}
