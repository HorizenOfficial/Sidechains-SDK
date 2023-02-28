package io.horizen.account.helper;

import com.horizen.account.node.AccountNodeView;

import java.util.function.Consumer;

public interface AccountNodeViewHelper {
    void getNodeView(Consumer<AccountNodeView> callback);
}
