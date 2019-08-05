package com.horizen.transaction;

import com.horizen.serialization.JsonSerializable;

public interface TransactionJsonSerializable extends JsonSerializable {

    @Override TransactionJsonSerializer jsonSerializer();

}
