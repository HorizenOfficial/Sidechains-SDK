package com.horizen.utils;

import scala.Enumeration;

// Used for parsing mc blocks retrieved from the MC node `getscgenesisinfo` RPC command after zendoo 3.0.3
public class NewSidechainsVersionsManager extends AbstractSidechainsVersionsManager {
    java.util.Map<ByteArrayWrapper, Enumeration.Value> scVersions;

    public NewSidechainsVersionsManager(java.util.Map<ByteArrayWrapper, Enumeration.Value> scVersions) {
        this.scVersions = scVersions;
    }

    @Override
    public Enumeration.Value getVersion(ByteArrayWrapper sidechainId) {
        return scVersions.get(sidechainId);
    }
}
