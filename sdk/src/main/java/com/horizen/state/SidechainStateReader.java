package com.horizen.state;

import com.horizen.box.*;
import com.horizen.utils.WithdrawalEpochInfo;

import java.util.Optional;
import java.util.List;

public interface SidechainStateReader {
    Optional<Box> getClosedBox(byte[] boxId);
}
