package com.horizen.state;

import com.horizen.box.Box;
import com.horizen.proposition.Proposition;

import java.util.List;
import java.util.Optional;

public interface SidechainStateReader {
    Optional<Box> getClosedBox(byte[] boxId);

    List<Box> getClosedBoxes(List<byte[]> boxIdsToExclude);

    List<Box> getClosedBoxesOfType(Class<? extends Box> type, List<byte[]> boxIdsToExclude);
}
