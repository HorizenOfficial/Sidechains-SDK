package io.horizen.account.helper;

import io.horizen.account.node.AccountNodeView;

import java.util.function.Consumer;

public interface AccountNodeViewHelper {
    void getNodeView(Consumer<AccountNodeView> callback);
}
