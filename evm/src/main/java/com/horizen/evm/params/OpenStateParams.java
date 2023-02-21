package com.horizen.evm.params;

import com.horizen.evm.Hash;

public class OpenStateParams extends DatabaseParams {
    public final Hash root;

    public OpenStateParams(int databaseHandle, Hash root) {
        super(databaseHandle);
        this.root = root;
    }
}
