package com.horizen.account.api.rpc.types;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.horizen.account.api.rpc.handler.RpcException;
import com.horizen.account.api.rpc.utils.RpcCode;
import com.horizen.account.api.rpc.utils.RpcError;
import com.horizen.evm.utils.Address;
import com.horizen.evm.utils.Hash;

import java.util.Arrays;

/**
 * Replicated from the original implementation in GETH, see:
 * github.com/ethereum/go-ethereum@v1.10.26/eth/filters/api.go:447
 * github.com/ethereum/go-ethereum@v1.10.26/ethclient/ethclient.go:390
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class FilterQuery {
    /**
     * used by eth_getLogs, return logs only from block with this hash
     */
    public Hash blockHash;

    /**
     * beginning of the queried range, null means genesis block
     */
    public String fromBlock;

    /**
     * end of the range, null means the latest block
     */
    public String toBlock;

    /**
     * Restricts matches to events created by specific contracts.
     * <p>
     * Note: Address can be a single address or an array of addresses.
     * Deserialization is configured in a way to accept single values and parse them as an array with a single value.
     * </p>
     */
    public Address[] address;

    /**
     * The Topic list restricts matches to particular event topics. Each event has a list
     * of topics. `Topics` matches a prefix of that list. An empty element slice matches any
     * topic. Non-empty elements represent an alternative that matches any of the
     * contained topics.
     * Examples:
     * <ul>
     * <li>{} or null         matches any topic list</li>
     * <li>{{A}}              matches topic A in first position</li>
     * <li>{{}, {B}}          matches any topic in first position AND B in second position</li>
     * <li>{{A}, {B}}         matches topic A in first position AND B in second position</li>
     * <li>{{A, B}, {C, D}}   matches topic (A OR B) in first position AND (C OR D) in second position</li>
     * </ul>
     */
    public Hash[][] topics;

    public void sanitize() throws RpcException {
        if (blockHash != null) {
            if (fromBlock != null || toBlock != null) {
                throw new RpcException(RpcError.fromCode(
                    RpcCode.InvalidParams,
                    "cannot specify both BlockHash and FromBlock/ToBlock, choose one or the other"
                ));
            }
        } else {
            if (fromBlock == null) fromBlock = "earliest";
            if (toBlock == null) toBlock = "latest";
        }
        if (address == null) {
            address = new Address[0];
        }
        // TODO: sanitize topics
        if (topics == null) {
            topics = new Hash[0][0];
        }
//        else if (topics.length > 0) {
//            for (int i = 0; i < topics.length; i++) {
//                Hash[] sub = topics[i];
//                if (sub == null) {
//                    topics[i] = new Hash[0];
//                }
//            }
//        }
    }

    @Override
    public String toString() {
        return String.format(
            "FilterQuery{fromBlock='%s', toBlock='%s', addresses=%s, topics=%s, blockHash=%s}",
            fromBlock, toBlock, Arrays.toString(address), Arrays.toString(topics), blockHash
        );
    }
}
