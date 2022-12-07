package com.horizen.account.api.rpc.types;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.horizen.evm.utils.Address;
import com.horizen.evm.utils.Hash;

import java.util.Arrays;

@JsonIgnoreProperties(ignoreUnknown = true)
public class FilterQuery {
    public String fromBlock;
    public String toBlock;
    public Address[] addresses;
    public Hash[][] topics;
    public Hash blockHash;

    @Override
    public String toString() {
        return String.format(
            "FilterQuery{fromBlock='%s', toBlock='%s', addresses=%s, topics=%s, blockHash=%s}",
            fromBlock, toBlock, Arrays.toString(addresses), Arrays.toString(topics), blockHash
        );
    }
}
