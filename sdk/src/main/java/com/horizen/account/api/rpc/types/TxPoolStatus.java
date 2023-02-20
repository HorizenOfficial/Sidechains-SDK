package com.horizen.account.api.rpc.types;

public class TxPoolStatus {

    public final int pending;
    public final int queued;

    public TxPoolStatus(int pending, int queued) {
        this.pending = pending;
        this.queued = queued;
    }
}
