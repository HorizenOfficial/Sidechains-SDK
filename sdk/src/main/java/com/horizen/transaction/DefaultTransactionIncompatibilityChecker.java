package com.horizen.transaction;

import com.horizen.ClosedBoxesZendooMerkleTree;
import com.horizen.box.Box;
import com.horizen.box.BoxUnlocker;
import com.horizen.utils.ByteArrayWrapper;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

public class DefaultTransactionIncompatibilityChecker
    implements TransactionIncompatibilityChecker
{
    //@TODO remove it, static function shall be used
    static Random rnd = new Random();
    static String statePath = new File(System.getProperty("java.io.tmpdir") + File.separator + "-" + Math.abs(rnd.nextInt())).getAbsolutePath();
    static String dbPath = new File(System.getProperty("java.io.tmpdir") + File.separator + "-" + Math.abs(rnd.nextInt())).getAbsolutePath();
    static String cachePath = new File(System.getProperty("java.io.tmpdir") + File.separator + "-" + Math.abs(rnd.nextInt())).getAbsolutePath();
    private static final ClosedBoxesZendooMerkleTree tree = new ClosedBoxesZendooMerkleTree(statePath, dbPath, cachePath);

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
        List<Long> currentTxsOutputBoxesPosition = currentTxs.stream().flatMap(tx -> {
            List<Box> boxes = tx.newBoxes();
            return boxes.stream().map(box -> tree.getPositionForBoxId(new ByteArrayWrapper(box.id())));
        }).collect(Collectors.toList());

        List<Box> newTxsBoxes = newTx.newBoxes();
        List<Long> newTxOutputBoxesPosition = newTxsBoxes.stream().map(box -> tree.getPositionForBoxId(new ByteArrayWrapper(box.id()))).collect(Collectors.toList());

        List<Long> totalOutputPositions = new ArrayList<>();
        totalOutputPositions.addAll(currentTxsOutputBoxesPosition);
        totalOutputPositions.addAll(newTxOutputBoxesPosition);

        Set<Long> totalOutputPositionsAsSet = new HashSet<>(totalOutputPositions);
        return (totalOutputPositions.size() == totalOutputPositionsAsSet.size());
    }

    @Override
    public boolean isMemoryPoolCompatible() {
        return true;
    }
}