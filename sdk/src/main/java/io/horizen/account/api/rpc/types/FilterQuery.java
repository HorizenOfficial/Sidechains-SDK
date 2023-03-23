package io.horizen.account.api.rpc.types;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.node.JsonNodeType;
import io.horizen.account.api.rpc.handler.RpcException;
import io.horizen.account.api.rpc.utils.RpcCode;
import io.horizen.account.api.rpc.utils.RpcError;
import io.horizen.evm.Address;
import io.horizen.evm.Hash;

import java.io.IOException;
import java.util.Arrays;
import java.util.Objects;

/**
 * Replicated from the original implementation in GETH, see:
 * github.com/ethereum/go-ethereum@v1.10.26/eth/filters/api.go:447
 * github.com/ethereum/go-ethereum@v1.10.26/ethclient/ethclient.go:390
 */
public class FilterQuery {
    /**
     * used by eth_getLogs, return logs only from block with this hash
     */
    public final Hash blockHash;

    /**
     * beginning of the queried range, null means genesis block
     */
    public final String fromBlock;

    /**
     * end of the range, null means the latest block
     */
    public final String toBlock;

    /**
     * Restricts matches to events created by specific contracts.
     * <p>
     * Note: Address can be a single address or an array of addresses.
     * Deserialization is configured in a way to accept single values and parse them as an array with a single value.
     * </p>
     */
    @JsonDeserialize(using = AddressFilterDeserializer.class)
    public final Address[] address;

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
    @JsonDeserialize(using = TopicFilterDeserializer.class)
    public final Hash[][] topics;

    public FilterQuery(
        @JsonProperty("blockHash") Hash blockHash,
        @JsonProperty("fromBlock") String fromBlock,
        @JsonProperty("toBlock") String toBlock,
        @JsonProperty("address") Address[] address,
        @JsonProperty("topics") Hash[][] topics
    ) throws RpcException {
        if (blockHash != null && (fromBlock != null || toBlock != null)) {
            throw new RpcException(RpcError.fromCode(
                RpcCode.InvalidParams,
                "cannot specify both BlockHash and FromBlock/ToBlock, choose one or the other"
            ));
        }
        if (blockHash == null) {
            if (fromBlock == null) fromBlock = "earliest";
            if (toBlock == null) toBlock = "latest";
        }
        this.blockHash = blockHash;
        this.fromBlock = fromBlock;
        this.toBlock = toBlock;
        this.address = Objects.requireNonNullElse(address, new Address[0]);
        this.topics = Objects.requireNonNullElse(topics, new Hash[0][0]);
    }

    private static class AddressFilterDeserializer extends JsonDeserializer<Address[]> {
        @Override
        public Address[] deserialize(JsonParser jsonParser, DeserializationContext context) throws IOException {
            JsonNode node = jsonParser.getCodec().readTree(jsonParser);
            switch (node.getNodeType()) {
                case STRING:
                    try {
                        return new Address[] {context.readTreeAsValue(node, Address.class)};
                    } catch (Exception err) {
                        throw new IOException(String.format("invalid address: %s", err.getMessage()), err);
                    }
                case ARRAY:
                    var addresses = new Address[node.size()];
                    for (int i = 0; i < node.size(); i++) {
                        var address = node.get(i);
                        if (address.getNodeType() != JsonNodeType.STRING) {
                            throw new IOException(String.format("non-string address at index %d", i));
                        }
                        try {
                            addresses[i] = context.readTreeAsValue(address, Address.class);
                        } catch (Exception err) {
                            throw new IOException(
                                String.format("invalid address at index %d: %s", i, err.getMessage()), err);
                        }
                    }
                    return addresses;
                default:
                    throw new IOException("invalid addresses in query");
            }
        }
    }

    private static class TopicFilterDeserializer extends JsonDeserializer<Hash[][]> {
        @Override
        public Hash[][] deserialize(JsonParser jsonParser, DeserializationContext context) throws IOException {
            JsonNode node = jsonParser.getCodec().readTree(jsonParser);
            if (!node.isArray()) {
                throw new IOException("invalid topic filter, expected an array");
            }
            if (node.size() == 0) {
                return new Hash[0][0];
            }
            var topics = new Hash[node.size()][];
            for (int i = 0; i < node.size(); i++) {
                var topic = node.get(i);
                switch (topic.getNodeType()) {
                    case NULL:
                        // ignore topic when matching logs
                        topics[i] = new Hash[0];
                        break;
                    case STRING: {
                        // match specific topic
                        topics[i] = new Hash[] {context.readTreeAsValue(topic, Hash.class)};
                        break;
                    }
                    case ARRAY: {
                        // or case e.g. [null, "topic0", "topic1"]
                        topics[i] = new Hash[topic.size()];
                        for (var j = 0; j < topic.size(); j++) {
                            var subTopic = topic.get(j);
                            if (subTopic.isNull()) {
                                // null component, match all
                                topics[i] = new Hash[0];
                                break;
                            }
                            if (subTopic.isTextual()) {
                                topics[i][j] = context.readTreeAsValue(subTopic, Hash.class);
                                continue;
                            }
                            throw new IOException("invalid topic(s)");
                        }
                        break;
                    }
                    default:
                        throw new IOException("invalid topic(s)");
                }
            }
            return topics;
        }
    }

    @Override
    public String toString() {
        return String.format(
            "FilterQuery{fromBlock='%s', toBlock='%s', addresses=%s, topics=%s, blockHash=%s}",
            fromBlock, toBlock, Arrays.toString(address), Arrays.toString(topics), blockHash
        );
    }
}
