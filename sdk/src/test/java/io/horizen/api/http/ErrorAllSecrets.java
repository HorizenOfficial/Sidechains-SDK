package io.horizen.api.http;

import java.util.Optional;

public class ErrorAllSecrets implements ErrorResponse {

    private String description;
    private Optional<Throwable> exception;

    public ErrorAllSecrets(String description, Optional<Throwable> exception) {
        this.description = description;
        this.exception = exception;
    }

    @Override
    public String code() {
        return "0901";
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
