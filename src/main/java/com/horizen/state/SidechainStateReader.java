package com.horizen.state;

import com.horizen.box.Box;
import com.horizen.proposition.Proposition;

import java.util.Optional;

public interface SidechainStateReader {
    Optional<Box<Proposition>> getClosedBox(byte[] boxId);
}
