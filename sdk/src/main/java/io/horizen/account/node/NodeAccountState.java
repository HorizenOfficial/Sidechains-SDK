package io.horizen.account.node;

import io.horizen.account.state.AccountStateReader;
import io.horizen.node.NodeStateBase;
import io.horizen.state.BaseStateReader;

public interface NodeAccountState extends NodeStateBase, AccountStateReader, BaseStateReader {

}
