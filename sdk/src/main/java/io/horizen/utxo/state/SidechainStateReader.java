package io.horizen.utxo.state;

import com.horizen.utxo.box.*;
import com.horizen.utxo.box.Box;

import java.util.Optional;

public interface SidechainStateReader {
    Optional<Box> getClosedBox(byte[] boxId);
}
