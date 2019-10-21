package com.horizen.api.http;

import com.horizen.node.NodeWallet;

import java.util.List;

public interface ExtendedNodeWallet extends NodeWallet {

    List<ExtendedProposition> allPublicKeys();
}
