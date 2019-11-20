package com.horizen.state;

import com.horizen.box.*;

import java.util.Optional;
import java.util.List;

public interface SidechainStateReader {
    Optional<Box> getClosedBox(byte[] boxId);
    List<WithdrawalRequestBox> getWithdrawalRequests(Integer epoch);
}
