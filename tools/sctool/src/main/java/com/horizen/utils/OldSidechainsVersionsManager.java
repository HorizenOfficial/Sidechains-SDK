package com.horizen.utils;

import com.horizen.block.SidechainCreationVersions;
import scala.Enumeration;

// Used for parsing mc blocks retrieved from the MC node `getscgenesisinfo` RPC command for zendoo 3.0.3 and older
// Always return version zero, since the MC node has no info about sc versions, because they are always zero.
public class OldSidechainsVersionsManager extends AbstractSidechainsVersionsManager {

    public OldSidechainsVersionsManager() {}

    @Override
    public Enumeration.Value getVersion(ByteArrayWrapper sidechainId) {
        return SidechainCreationVersions.SidechainCreationVersion0();
    }
}