package io.horizen.examples.api.model;

import io.horizen.api.http.ErrorResponse;

import java.util.Optional;

public final class ErrorResponseTx implements ErrorResponse {
    private final String code;
    private final String description;
    private final Optional<Throwable> exception;

    public ErrorResponseTx(String code, String description, Optional<Throwable> exception) {
        this.code = code;
        this.description = description;
        this.exception = exception;
    }

    @Override
    public String code() {
        return code;
    }

    @Override
    public String description() {
        return description;
    }

    @Override
    public Optional<Throwable> exception() {
        return exception;
    }
}