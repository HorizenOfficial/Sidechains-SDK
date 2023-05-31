package io.horizen.examples.api.model;

import io.horizen.api.http.ErrorResponse;
import scala.reflect.internal.Reporter;

import java.util.Optional;

public final class ErrorResponseTx implements ErrorResponse {
    private static final String ERROR_RESPONSE_CODE = "0001";
    private static final String ERROR_RESPONSE_DESCRIPTION = "Error response";
    @Override
    public String code() {
        return ERROR_RESPONSE_CODE;
    }

    @Override
    public String description() {
        return ERROR_RESPONSE_DESCRIPTION;
    }

    @Override
    public Optional<Throwable> exception() {
        return Optional.empty();
    }
}
