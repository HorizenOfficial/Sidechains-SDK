package com.horizen.api.http;

import scala.Option;

public class ErrorAllSecrets implements ErrorResponse {

    private String description;
    private Option<Throwable> exception;

    public ErrorAllSecrets(String description, Option<Throwable> exception) {
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
    public Option<Throwable> exception() {
        return exception;
    }
}
