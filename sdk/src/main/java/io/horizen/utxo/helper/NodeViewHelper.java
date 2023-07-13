package io.horizen.utxo.helper;

import io.horizen.utxo.node.SidechainNodeView;

import java.util.function.Consumer;

public interface NodeViewHelper {
    void getNodeView(Consumer<SidechainNodeView> callback);
}
