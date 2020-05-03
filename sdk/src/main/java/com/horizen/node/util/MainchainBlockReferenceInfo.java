package com.horizen.node.util;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonView;
import com.google.common.primitives.Bytes;
import com.google.common.primitives.Ints;
import com.horizen.CommonParams;
import com.horizen.serialization.Views;
import com.horizen.utils.BytesUtils;
import scorex.core.serialization.BytesSerializable;
import scorex.core.serialization.ScorexSerializer;

import java.util.Arrays;
import java.util.Objects;

@JsonView(Views.Default.class)
public final class MainchainBlockReferenceInfo implements BytesSerializable {

    // Mainchain block reference header hash with the most height
    @JsonProperty("hash")
    private byte[] mainchainHeaderHash;

    // parent mainchain block reference hash
    @JsonProperty("parentHash")
    private byte[] parentMainchainHeaderHash;

    // Height in mainchain of mainchainBlockReference
    @JsonProperty("height")
    private int mainchainHeight;

    // Sidechain block ID which contains this MC block reference header
    private byte[] mainchainHeaderSidechainBlockId;

    // Sidechain block ID which contains this MC block reference data
    private byte[] mainchainReferenceDataSidechainBlockId;

    public MainchainBlockReferenceInfo(byte[] mainchainHeaderHash,
                                       byte[] parentMainchainHeaderHash,
                                       int mainchainHeight,
                                       byte[] mainchainHeaderSidechainBlockId,
                                       byte[] mainchainReferenceDataSidechainBlockId) {
        assert (mainchainHeaderHash.length == CommonParams.mainchainBlockHashLength());
        this.mainchainHeaderHash = Arrays.copyOf(mainchainHeaderHash, mainchainHeaderHash.length);

        assert (parentMainchainHeaderHash.length == CommonParams.mainchainBlockHashLength());
        this.parentMainchainHeaderHash = Arrays.copyOf(parentMainchainHeaderHash, parentMainchainHeaderHash.length);
        this.mainchainHeight = mainchainHeight;

        assert (mainchainHeaderSidechainBlockId.length == CommonParams.sidechainIdLength());
        this.mainchainHeaderSidechainBlockId = Arrays.copyOf(mainchainHeaderSidechainBlockId, mainchainHeaderSidechainBlockId.length);

        assert (mainchainReferenceDataSidechainBlockId.length == CommonParams.sidechainIdLength());
        this.mainchainReferenceDataSidechainBlockId = Arrays.copyOf(mainchainReferenceDataSidechainBlockId, mainchainReferenceDataSidechainBlockId.length);
    }

    public byte[] getMainchainHeaderHash() {
        return mainchainHeaderHash;
    }

    public byte[] getParentMainchainHeaderHash() {
        return parentMainchainHeaderHash;
    }

    public int getMainchainHeight() {
        return mainchainHeight;
    }

    public byte[] getMainchainHeaderSidechainBlockId() {
        return mainchainHeaderSidechainBlockId;
    }

    public byte[] getMainchainReferenceDataSidechainBlockId() {
        return mainchainReferenceDataSidechainBlockId;
    }

    public static MainchainBlockReferenceInfo parseBytes(byte[] bytes) {
        int offset = 0;

        byte[] mainchainBlockReferenceHash = Arrays.copyOfRange(bytes, offset, offset + CommonParams.mainchainBlockHashLength());
        offset += CommonParams.mainchainBlockHashLength();

        byte[] parentMainchainBlockReferenceHash = Arrays.copyOfRange(bytes, offset, offset + CommonParams.mainchainBlockHashLength());
        offset += CommonParams.mainchainBlockHashLength();

        int mainchainHeight = BytesUtils.getInt(bytes, offset);
        offset += Integer.BYTES;

        byte[] mainchainHeaderSidechainBlockId = Arrays.copyOfRange(bytes, offset, offset + CommonParams.sidechainIdLength());
        offset += CommonParams.sidechainIdLength();

        byte[] mainchainReferenceDataSidechainBlockId = Arrays.copyOfRange(bytes, offset, offset + CommonParams.sidechainIdLength());
        offset += CommonParams.sidechainIdLength();

        return new MainchainBlockReferenceInfo(
                mainchainBlockReferenceHash,
                parentMainchainBlockReferenceHash,
                mainchainHeight,
                mainchainHeaderSidechainBlockId,
                mainchainReferenceDataSidechainBlockId);
    }

    @Override
    public byte[] bytes() {
        return Bytes.concat(
                mainchainHeaderHash,
                parentMainchainHeaderHash,
                Ints.toByteArray(mainchainHeight),
                mainchainHeaderSidechainBlockId,
                mainchainReferenceDataSidechainBlockId);
    }

    @Override
    public ScorexSerializer<BytesSerializable> serializer() {
        return MainchainBlockReferenceInfoSerializer.getSerializer();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MainchainBlockReferenceInfo that = (MainchainBlockReferenceInfo) o;
        return mainchainHeight == that.mainchainHeight &&
                Arrays.equals(mainchainHeaderHash, that.mainchainHeaderHash) &&
                Arrays.equals(parentMainchainHeaderHash, that.parentMainchainHeaderHash) &&
                Arrays.equals(mainchainHeaderSidechainBlockId, that.mainchainHeaderSidechainBlockId) &&
                Arrays.equals(mainchainReferenceDataSidechainBlockId, that.mainchainReferenceDataSidechainBlockId);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(mainchainHeight);
        result = 31 * result + Arrays.hashCode(mainchainHeaderHash);
        result = 31 * result + Arrays.hashCode(parentMainchainHeaderHash);
        result = 31 * result + Arrays.hashCode(mainchainHeaderSidechainBlockId);
        result = 31 * result + Arrays.hashCode(mainchainReferenceDataSidechainBlockId);
        return result;
    }
}
