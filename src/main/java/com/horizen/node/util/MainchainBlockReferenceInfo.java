package com.horizen.node.util;

public final class MainchainBlockReferenceInfo {

    // Mainchain block reference hash with the most height
    private byte[] mainchainBlockReferenceHash;

    // Height in mainchain of mainchainBlockReference
    private int height;

    // Sidechain block ID which contains this MC block reference
    private byte[] sidechainBlockId;

    public MainchainBlockReferenceInfo(byte[] mainchainBlockReferenceHash, int height, byte[] sidechainBlockId) {
        this.mainchainBlockReferenceHash = mainchainBlockReferenceHash;
        this.height = height;
        this.sidechainBlockId = sidechainBlockId;
    }

    public byte[] getMainchainBlockReferenceHash() {
        return mainchainBlockReferenceHash;
    }

    public void setMainchainBlockReferenceHash(byte[] mainchainBlockReferenceHash) {
        this.mainchainBlockReferenceHash = mainchainBlockReferenceHash;
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

    public void setSidechainBlockId(byte[] sidechainBlockId) {
        this.sidechainBlockId = sidechainBlockId;
    }

}
