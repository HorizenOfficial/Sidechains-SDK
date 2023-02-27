package com.horizen.utxo.helper;

import com.horizen.utxo.node.SidechainNodeView;
import java.util.function.Consumer;

public interface NodeViewHelper {
    void getNodeView(Consumer<SidechainNodeView> callback);
}
