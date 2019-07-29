package com.horizen.node;


import com.horizen.box.Box;

import java.util.List;

public interface NodeState {

    List<Box> getClosedBoxes(List<byte[]> boxIdsToExclude);

    List<Box> getClosedBoxesOfType(Class<? extends Box> type, List<byte[]> boxIdsToExclude);

}
