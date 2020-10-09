package com.horizen.transaction;

import com.horizen.ClosedBoxesZendooMerkleTree;
import com.horizen.box.Box;
import com.horizen.box.BoxUnlocker;
import com.horizen.utils.ByteArrayWrapper;

import java.util.*;
import java.util.stream.Collectors;

public class DefaultTransactionIncompatibilityChecker
    implements TransactionIncompatibilityChecker
{
    @Override
    public <T extends BoxTransaction> boolean isTransactionCompatible(T newTx, List<T> currentTxs) {
        if(newTx == null || currentTxs == null)
            throw new IllegalArgumentException("Parameters can't be null.");

        return (checkInputBoxesExclusivity(newTx, currentTxs) && checkOutputBoxesPositionExclusivity(newTx, currentTxs));
    }

    private <T extends BoxTransaction> boolean checkInputBoxesExclusivity(T newTx, List<T> currentTxs) {
        // Check intersections between spent boxes of newTx and currentTxs
        // Algorithm difficulty is O(n+m), where n - number of spent boxes in newTx, m - number of currentTxs
        // Note: .boxIdsToOpen() and .unlockers() expected to be optimized (lazy calculated)
        for(BoxUnlocker unlocker : (List<BoxUnlocker>)newTx.unlockers()) {
            ByteArrayWrapper closedBoxId = new ByteArrayWrapper(unlocker.closedBoxId());
            for (BoxTransaction tx : currentTxs) {
                if(tx.boxIdsToOpen().contains(closedBoxId))
                    return false;
            }
        }
        return true;
    }

    private <T extends BoxTransaction> boolean checkOutputBoxesPositionExclusivity(T newTx, List<T> currentTxs) {
        List<Long> currentTxsOutputBoxesPositionList = currentTxs.stream().flatMap(tx -> {
            List<Box> boxes = tx.newBoxes();
            return boxes.stream().map(box -> ClosedBoxesZendooMerkleTree.getFieldElementPositionForBoxId(new ByteArrayWrapper(box.id())));
        }).collect(Collectors.toList());

        Set<Long> currentTxsOutputBoxesPositionSet = new HashSet<>(currentTxsOutputBoxesPositionList);
        if (currentTxsOutputBoxesPositionList.size() != currentTxsOutputBoxesPositionSet.size()) {
            throw new IllegalStateException("Transaction had been found with the same output box position in closed boxes merkle tree in memory pool");
        }

        List<Box> newTxsBoxes = newTx.newBoxes();
        boolean duplicateInPosition = newTxsBoxes
            .stream()
            .map(box -> ClosedBoxesZendooMerkleTree.getFieldElementPositionForBoxId(new ByteArrayWrapper(box.id())))
            .allMatch(position -> (!currentTxsOutputBoxesPositionSet.contains(position)));

        return duplicateInPosition;
    }

    @Override
    public boolean isMemoryPoolCompatible() {
        return true;
    }
}