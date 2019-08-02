package com.horizen.transaction;

import com.horizen.serialization.JsonSerializer;

public interface TransactionJsonSerializer<T extends Transaction> extends JsonSerializer<T> {

}
