package io.horizen.examples.api.model;

import com.fasterxml.jackson.annotation.JsonView;
import io.horizen.api.http.SuccessResponse;
import io.horizen.json.Views;

@JsonView(Views.Default.class)
public final class SuccessResponseTx implements SuccessResponse {
    private final String transactionBytes;

    public SuccessResponseTx(String transactionBytes) {
        this.transactionBytes = transactionBytes;
    }

    public String getTransactionBytes() {
        return transactionBytes;
    }

    @Override
    public String toString() {
        return transactionBytes;
    }
}