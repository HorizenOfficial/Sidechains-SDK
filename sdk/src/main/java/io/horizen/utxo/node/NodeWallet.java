package io.horizen.utxo.node;

import io.horizen.node.NodeWalletBase;
import io.horizen.utxo.box.Box;
import io.horizen.proposition.Proposition;

import java.util.List;

public interface NodeWallet extends NodeWalletBase {

    // boxes are sorted by creation time in wallet from oldest to newest
    List<Box<Proposition>> allBoxes();

    // boxes are sorted by creation time in wallet from oldest to newest
    List<Box<Proposition>> allBoxes(List<byte[]> boxIdsToExclude);

    List<Box<Proposition>> boxesOfType(Class<? extends Box<? extends Proposition>> type);

    List<Box<Proposition>> boxesOfType(Class<? extends Box<? extends Proposition>> type, List<byte[]> boxIdsToExclude);

    Long boxesBalance(Class<? extends Box<? extends Proposition>> type);

    Long allCoinsBoxesBalance();
}
