package com.horizen.api.http;

import com.horizen.api.http.schema.ApiGroupErrorCodes;

public class MyCustomErrorCodes implements ApiGroupErrorCodes {

    @Override
    public String code() {
        return "04";
    }

    public final String RESOURCE_NOT_FOUND = code()+"01";
}
