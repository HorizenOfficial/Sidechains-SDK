package io.horizen.utxo.state;

import io.horizen.utxo.box.Box;

import java.util.Optional;

public interface SidechainStateReader {
    Optional<Box> getClosedBox(byte[] boxId);
}
