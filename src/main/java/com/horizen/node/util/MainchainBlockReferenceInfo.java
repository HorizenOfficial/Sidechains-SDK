package com.horizen.node.util;

import java.util.Arrays;

public final class MainchainBlockReferenceInfo {

    // Mainchain block reference hash with the most height
    private byte[] mainchainBlockReferenceHash;

    // parent mainchain block reference hash with the most height
    private byte[] parentMainchainBlockReferenceHash;

    // Height in mainchain of mainchainBlockReference
    private int height;

    // Sidechain block ID which contains this MC block reference
    private byte[] sidechainBlockId;

    public MainchainBlockReferenceInfo(byte[] mainchainBlockReferenceHash,
                                       byte[] parentMainchainBlockReferenceHash,
                                       int height,
                                       byte[] sidechainBlockId) {
        this.mainchainBlockReferenceHash = Arrays.copyOf(mainchainBlockReferenceHash, mainchainBlockReferenceHash.length);
        this.parentMainchainBlockReferenceHash = Arrays.copyOf(parentMainchainBlockReferenceHash, parentMainchainBlockReferenceHash.length);
        this.height = height;
        this.sidechainBlockId = Arrays.copyOf(sidechainBlockId, sidechainBlockId.length);
    }

    public byte[] getMainchainBlockReferenceHash() {
        return mainchainBlockReferenceHash;
    }

    public byte[] getParentMainchainBlockReferenceHash() {
        return parentMainchainBlockReferenceHash;
    }

    public int getHeight() {
        return height;
    }

    public void setHeight(int height) {
        this.height = height;
    }

    public byte[] getSidechainBlockId() {
        return sidechainBlockId;
    }
}
