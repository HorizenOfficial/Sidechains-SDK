package com.horizen.api.http;

public class MyCustomErrorCodes implements ApiGroupErrorCodes {

    @Override
    public String code() {
        return "04";
    }

    public final String RESOURCE_NOT_FOUND = code()+"01";
}
