package com.horizen.cryptolibprovider;

import com.horizen.librustsidechains.FieldElement;
import com.horizen.utils.BytesUtils;
import org.junit.Ignore;
import org.junit.Test;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import static org.junit.Assert.*;

public class InMemorySparseMerkleTreeWrapperTest {
    final int treeHeight = 10;
    long totalLeavesNumber = 1 << treeHeight;

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
        assertEquals("No positions expected.", totalLeavesNumber, merkleTreeWrapper.leftmostEmptyPositions(totalLeavesNumber + 1).size());
        assertEquals("No positions expected.", 0, merkleTreeWrapper.leftmostEmptyPositions(0).size());
        assertEquals("No positions expected.", 0, merkleTreeWrapper.leftmostEmptyPositions(-1).size());

        try {
            merkleTreeWrapper.close();
        } catch (Exception ignored) {}
    }

    @Test
    public void root() {
        InMemorySparseMerkleTreeWrapper merkleTreeWrapper = new InMemorySparseMerkleTreeWrapper(treeHeight);
        byte[] root = merkleTreeWrapper.calculateRoot();
        String rootHex = BytesUtils.toHexString(root);
        assertEquals("Root of empty tree is different.", "cae22c26168c9275bfa5ad7aa496e94450367a19be9a142e2c6a8d3f5afaaf26", rootHex);

        try {
            merkleTreeWrapper.close();
        } catch (Exception ignored) {}
    }

    @Test
    public void addLeaves() {
        InMemorySparseMerkleTreeWrapper merkleTreeWrapper = new InMemorySparseMerkleTreeWrapper(treeHeight);

        // Test: append leaves to empty positions
        Map<Long, FieldElement> leavesToAppend = new HashMap();
        leavesToAppend.put(0L, FieldElement.createRandom(123L));
        leavesToAppend.put(1L, FieldElement.createRandom(456L));
        leavesToAppend.put(10L, FieldElement.createRandom(789L));
        leavesToAppend.put(15L, FieldElement.createRandom(111L));
        leavesToAppend.put(16L, FieldElement.createRandom(222L));
        leavesToAppend.put(totalLeavesNumber - 1, FieldElement.createRandom(333L));

        assertTrue("Leaves expected to be added.", merkleTreeWrapper.addLeaves(leavesToAppend));

        for(long pos : leavesToAppend.keySet()) {
            assertFalse("Leaf expected to be occupied.", merkleTreeWrapper.isLeafEmpty(pos));
        }

        byte[] root = merkleTreeWrapper.calculateRoot();
        assertNotNull("Root should exist.", root);

        // Test: try to append the same leaves second time.
        assertFalse("Leaves expected to be added.", merkleTreeWrapper.addLeaves(leavesToAppend));

        Map<Long, FieldElement> submap = new HashMap<>();
        submap.put(0L, leavesToAppend.get(0L));
        submap.put(1L, leavesToAppend.get(1L));

        assertFalse("Leaves not expected to be added.", merkleTreeWrapper.addLeaves(submap));


        // Test: check empty positions
        assertEquals("Different leftmost empty position retrieved for a single leaf.",
                Collections.singletonList(2L), merkleTreeWrapper.leftmostEmptyPositions(1));

        List<Long> expectedEmptyPositions =
                LongStream.range(0L, 50L)
                        .filter(pos -> leavesToAppend.keySet().stream().noneMatch(key -> key == pos))
                        .boxed()
                        .collect(Collectors.toList());

        assertEquals("Different leftmost empty position retrieved for multiple leaves.",
                expectedEmptyPositions, merkleTreeWrapper.leftmostEmptyPositions(expectedEmptyPositions.size()));

        // Do cleanup
        leavesToAppend.values().forEach(leaf -> {
            try {
                leaf.close();
            } catch (Exception e) {
                fail(e.getMessage());
            }
        });

        try {
            merkleTreeWrapper.close();
        } catch (Exception ignored) {}
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
        Map<Long, FieldElement> leavesToAppend = new HashMap();
        leavesToAppend.put(0L, FieldElement.createRandom(456L));
        leavesToAppend.put(10L, FieldElement.createRandom(789L));
        leavesToAppend.put(15L, FieldElement.createRandom(111L));

        assertTrue("Leaves expected to be added.", merkleTreeWrapper.addLeaves(leavesToAppend));

        long[] leavesToRemove = leavesToAppend.keySet().stream().mapToLong(Long::longValue).toArray();
        assertTrue("Leaves expected to be removed.", merkleTreeWrapper.removeLeaves(leavesToRemove));

        for(long pos : leavesToRemove) {
            assertTrue("Leaf expected to be empty.", merkleTreeWrapper.isLeafEmpty(pos));
        }

        List<Long> expectedEmptyPositions = LongStream.range(0L, 50L).boxed().collect(Collectors.toList());

        assertEquals("Different leftmost empty position retrieved for multiple leaves.",
                expectedEmptyPositions, merkleTreeWrapper.leftmostEmptyPositions(expectedEmptyPositions.size()));

        byte[] root = merkleTreeWrapper.calculateRoot();
        assertNotNull("Root should exist.", root);

        try {
            merkleTreeWrapper.close();
        } catch (Exception ignored) {}
    }
}
