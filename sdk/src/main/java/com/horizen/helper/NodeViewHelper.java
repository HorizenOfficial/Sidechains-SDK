package com.horizen.helper;

import com.horizen.node.SidechainNodeView;
import java.util.function.Consumer;

public interface NodeViewHelper {
    void getNodeView(Consumer<SidechainNodeView> callback);
}
