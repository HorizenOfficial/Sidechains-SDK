package io.horizen.evm.params;

public class SnapshotParams extends HandleParams {
    public final int revisionId;

    public SnapshotParams(int handle, int revisionId) {
        super(handle);
        this.revisionId = revisionId;
    }
}
