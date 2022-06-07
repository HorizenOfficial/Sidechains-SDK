package com.horizen.account.node;

import com.horizen.account.state.Account;
import com.horizen.account.state.AccountStateReader;
import com.horizen.node.NodeStateBase;

public interface NodeAccountState extends NodeStateBase, AccountStateReader {
    Account getAccount(byte[] address);
}
