package com.horizen.account.api.rpc.types;

import com.fasterxml.jackson.annotation.JsonView;
import com.horizen.serialization.Views;

@JsonView(Views.Default.class)
public class TxPoolStatus {

    private final int pending;
    private final int queued;

    public TxPoolStatus(int pending, int queued) {
        this.pending = pending;
        this.queued = queued;
    }

    public int getPending() {
        return pending;
    }

    public int getQueued() {
        return queued;
    }
}