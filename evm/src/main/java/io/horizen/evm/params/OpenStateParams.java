package io.horizen.evm.params;

import io.horizen.evm.Hash;

public class OpenStateParams extends DatabaseParams {
    public final Hash root;

    public OpenStateParams(int databaseHandle, Hash root) {
        super(databaseHandle);
        this.root = root;
    }
}
