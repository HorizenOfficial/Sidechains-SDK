package com.horizen.account.node;

import com.horizen.account.state.AccountStateReader;
import com.horizen.node.NodeStateBase;
import com.horizen.state.BaseStateReader;

public interface NodeAccountState extends NodeStateBase, AccountStateReader, BaseStateReader {

}
