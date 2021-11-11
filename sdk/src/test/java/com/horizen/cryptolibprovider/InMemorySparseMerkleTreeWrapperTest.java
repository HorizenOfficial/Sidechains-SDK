package com.horizen.cryptolibprovider;

import com.horizen.librustsidechains.FieldElement;
import com.horizen.utils.Pair;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.Assert.*;

public class InMemorySparseMerkleTreeWrapperTest {
    final int treeHeight = 10;
    int totalLeavesNumber = 1 << treeHeight;

    @Test
    public void checkEmptyPositions() {
        InMemorySparseMerkleTreeWrapper merkleTreeWrapper = new InMemorySparseMerkleTreeWrapper(treeHeight);

        // Test single leaf empty pos for empty tree
        assertEquals("Different leftmost empty position retrieved for a single leaf.",
                Arrays.asList(0), merkleTreeWrapper.leftmostEmptyPositions(1));

        // Test all empty positions for empty tree
        List<Integer> allEmptyPositions = merkleTreeWrapper.leftmostEmptyPositions(totalLeavesNumber);
        assertEquals("Different empty positions array size.", totalLeavesNumber, allEmptyPositions.size());
        assertEquals("Different empty position retrieved.", 0, allEmptyPositions.get(0).intValue());
        assertEquals("Different empty position retrieved.", totalLeavesNumber - 1, allEmptyPositions.get(allEmptyPositions.size() - 1).intValue());

        // Test out of range empty positions for empty tree
        assertNull("No positions expected.", merkleTreeWrapper.leftmostEmptyPositions(totalLeavesNumber + 1));
        assertNull("No positions expected.", merkleTreeWrapper.leftmostEmptyPositions(0));
        assertNull("No positions expected.", merkleTreeWrapper.leftmostEmptyPositions(-1));
    }

    @Test
    public void addLeaves() {
        InMemorySparseMerkleTreeWrapper merkleTreeWrapper = new InMemorySparseMerkleTreeWrapper(treeHeight);

        // Test: append leaves to empty positions
        List<Pair<FieldElement, Integer>> leavesToAppend = Arrays.asList(
                new Pair(FieldElement.createRandom(123L), 0),
                new Pair(FieldElement.createRandom(456L), 1),
                new Pair(FieldElement.createRandom(789L), 10),
                new Pair(FieldElement.createRandom(111L), 15),
                new Pair(FieldElement.createRandom(222L), 16),
                new Pair(FieldElement.createRandom(333L), totalLeavesNumber - 1)
        );

        assertTrue("Leaves expected to be added.", merkleTreeWrapper.addLeaves(leavesToAppend));

        for(Pair<FieldElement, Integer> leaf : leavesToAppend) {
            int pos = leaf.getValue();
            assertFalse("Leaf expected to be occupied.", merkleTreeWrapper.isLeafEmpty(pos));
        }


        // Test: try to append the same leaves second time.
        assertFalse("Leaves expected to be added.", merkleTreeWrapper.addLeaves(leavesToAppend));

        List<Pair<FieldElement, Integer>> sublist = leavesToAppend.subList(0, 2);
        assertFalse("Leaves expected to be added.", merkleTreeWrapper.addLeaves(sublist));


        // Test: check empty positions
        assertEquals("Different leftmost empty position retrieved for a single leaf.",
                Arrays.asList(2), merkleTreeWrapper.leftmostEmptyPositions(1));

        List<Integer> expectedEmptyPositions =
                IntStream.range(0, 50)
                        .filter(pos -> leavesToAppend.stream().noneMatch(pair -> pair.getValue() == pos))
                        .boxed()
                        .collect(Collectors.toList());

        assertEquals("Different leftmost empty position retrieved for multiple leaves.",
                expectedEmptyPositions, merkleTreeWrapper.leftmostEmptyPositions(expectedEmptyPositions.size()));

        // Do cleanup
        leavesToAppend.forEach(pair -> pair.getKey().freeFieldElement());
    }

    @Test
    public void removeLeaves() {
        InMemorySparseMerkleTreeWrapper merkleTreeWrapper = new InMemorySparseMerkleTreeWrapper(treeHeight);

        // Test: remove leaves from empty tree -> do nothing
        assertTrue("Leaves expected to be removed", merkleTreeWrapper.removeLeaves(Arrays.asList(0, 1, 2, 10)));


        // Test: try to remove leaf out of range
        assertFalse("Failure expected while removing leaf with out of range position.",
                merkleTreeWrapper.removeLeaves(Arrays.asList(totalLeavesNumber)));


        // Test: remove existing leaves:
        // Add leaves first
        List<Pair<FieldElement, Integer>> leavesToAppend = Arrays.asList(
                new Pair(FieldElement.createRandom(456L), 1),
                new Pair(FieldElement.createRandom(789L), 10),
                new Pair(FieldElement.createRandom(111L), 15)
        );
        assertTrue("Leaves expected to be added.", merkleTreeWrapper.addLeaves(leavesToAppend));

        List<Integer> leavesToRemove = leavesToAppend.stream().map(leaf -> leaf.getValue()).collect(Collectors.toList());

        assertTrue("Leaves expected to be removed.", merkleTreeWrapper.removeLeaves(leavesToRemove));

        for(int pos : leavesToRemove) {
            assertTrue("Leaf expected to be empty.", merkleTreeWrapper.isLeafEmpty(pos));
        }

        List<Integer> expectedEmptyPositions = IntStream.range(0, 50).boxed().collect(Collectors.toList());

        assertEquals("Different leftmost empty position retrieved for multiple leaves.",
                expectedEmptyPositions, merkleTreeWrapper.leftmostEmptyPositions(expectedEmptyPositions.size()));
    }
}
