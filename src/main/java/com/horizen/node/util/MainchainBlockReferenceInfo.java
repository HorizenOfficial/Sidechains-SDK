package com.horizen.node.util;

import com.google.common.primitives.Bytes;
import com.google.common.primitives.Ints;
import com.horizen.CommonParams;
import com.horizen.serialization.JsonSerializable;
import com.horizen.utils.BytesUtils;
import io.circe.Json;
import scorex.core.serialization.BytesSerializable;
import scorex.core.serialization.ScorexSerializer;

import java.util.Arrays;
import java.util.Objects;

public final class MainchainBlockReferenceInfo implements BytesSerializable, JsonSerializable {

    // Mainchain block reference hash with the most height
    private byte[] mainchainBlockReferenceHash;

    // parent mainchain block reference hash
    private byte[] parentMainchainBlockReferenceHash;

    // Height in mainchain of mainchainBlockReference
    private int mainchainHeight;

    // Sidechain block ID which contains this MC block reference
    private byte[] sidechainBlockId;

    public MainchainBlockReferenceInfo(byte[] mainchainBlockReferenceHash,
                                       byte[] parentMainchainBlockReferenceHash,
                                       int mainchainHeight,
                                       byte[] sidechainBlockId) {
        assert (mainchainBlockReferenceHash.length == CommonParams.mainchainBlockHashLength());
        this.mainchainBlockReferenceHash = Arrays.copyOf(mainchainBlockReferenceHash, mainchainBlockReferenceHash.length);

        assert (parentMainchainBlockReferenceHash.length == CommonParams.mainchainBlockHashLength());
        this.parentMainchainBlockReferenceHash = Arrays.copyOf(parentMainchainBlockReferenceHash, parentMainchainBlockReferenceHash.length);
        this.mainchainHeight = mainchainHeight;

        assert (sidechainBlockId.length == CommonParams.sidechainIdLength());
        this.sidechainBlockId = Arrays.copyOf(sidechainBlockId, sidechainBlockId.length);
    }

    public byte[] getMainchainBlockReferenceHash() {
        return mainchainBlockReferenceHash;
    }

    public byte[] getParentMainchainBlockReferenceHash() {
        return parentMainchainBlockReferenceHash;
    }

    public int getMainchainHeight() {
        return mainchainHeight;
    }

    public byte[] getSidechainBlockId() {
        return sidechainBlockId;
    }

    public static MainchainBlockReferenceInfo parseBytes(byte[] bytes) {
        int offset = 0;

        byte[] mainchainBlockReferenceHash = Arrays.copyOfRange(bytes, offset, offset + CommonParams.mainchainBlockHashLength());
        offset += CommonParams.mainchainBlockHashLength();

        byte[] parentMainchainBlockReferenceHash = Arrays.copyOfRange(bytes, offset, offset + CommonParams.mainchainBlockHashLength());
        offset += CommonParams.mainchainBlockHashLength();

        int mainchainHeight = BytesUtils.getInt(bytes, offset);
        offset += Integer.BYTES;

        byte[] sidechainBlockId = Arrays.copyOfRange(bytes, offset, offset + CommonParams.sidechainIdLength());
        offset += CommonParams.sidechainIdLength();

        return new MainchainBlockReferenceInfo(mainchainBlockReferenceHash, parentMainchainBlockReferenceHash, mainchainHeight, sidechainBlockId);
    }

    @Override
    public byte[] bytes() {
        return Bytes.concat(mainchainBlockReferenceHash, parentMainchainBlockReferenceHash, Ints.toByteArray(mainchainHeight), sidechainBlockId);
    }

    @Override
    public ScorexSerializer<BytesSerializable> serializer() {
        return MainchainBlockReferenceInfoSerializer.getSerializer();
    }

    @Override
    public Json toJson() {
        scala.collection.mutable.HashMap<String,Json> values = new scala.collection.mutable.HashMap<>();

        values.put("mainchainBlockReferenceHash", Json.fromString(BytesUtils.toHexString(this.mainchainBlockReferenceHash)));
        values.put("parentMainchainBlockReferenceHash", Json.fromString(BytesUtils.toHexString(this.parentMainchainBlockReferenceHash)));
        values.put("mainchainHeight", Json.fromInt(this.mainchainHeight));
        values.put("sidechainBlockId", Json.fromString(BytesUtils.toHexString(this.sidechainBlockId)));

        return Json.obj(values.toSeq());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MainchainBlockReferenceInfo that = (MainchainBlockReferenceInfo) o;
        return mainchainHeight == that.mainchainHeight &&
                Arrays.equals(mainchainBlockReferenceHash, that.mainchainBlockReferenceHash) &&
                Arrays.equals(parentMainchainBlockReferenceHash, that.parentMainchainBlockReferenceHash) &&
                Arrays.equals(sidechainBlockId, that.sidechainBlockId);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(mainchainHeight);
        result = 31 * result + Arrays.hashCode(mainchainBlockReferenceHash);
        result = 31 * result + Arrays.hashCode(parentMainchainBlockReferenceHash);
        result = 31 * result + Arrays.hashCode(sidechainBlockId);
        return result;
    }
}
