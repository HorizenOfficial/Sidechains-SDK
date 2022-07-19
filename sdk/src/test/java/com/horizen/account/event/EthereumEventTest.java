package com.horizen.account.event;

import com.horizen.evm.interop.EvmLog;
import org.junit.Before;
import org.junit.Test;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.rlp.RlpEncoder;
import org.web3j.rlp.RlpList;
import org.web3j.rlp.RlpString;
import org.web3j.rlp.RlpType;
import org.web3j.utils.Numeric;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;

public class EthereumEventTest {

    public class MyEvent1 {
        @Indexed
        @Parameter(1)
        public final Address from;

        @Indexed
        @Parameter(3)
        public final Address to;

        @Parameter(2)
        public final Uint256 value;

        public MyEvent1(Address from, Address to, Uint256 value) {
            this.from = from;
            this.to = to;
            this.value = value;
        }
    }

    @Anonymous
    public class MyEvent2 {
        private Address from;
        private Address to;
        private Uint256 value;

        public MyEvent2(Address from, Address to, Uint256 value) {
            this.from = from;
            this.to = to;
            this.value = value;
        }

        @Parameter(1)
        @Indexed
        public Address getFrom() {
            return this.from;
        }

        @Parameter(2)
        @Indexed
        public Address getTo() {
            return this.to;
        }

        @Parameter(3)
        public Uint256 getValue() {
            return this.value;
        }
    }

    @Before
    public void BeforeEachTest() {
    }

    @Test
    public void ethereumEventTest() throws ClassNotFoundException, IOException, IllegalAccessException, InvocationTargetException {
        MyEvent1 event1 = new MyEvent1(new Address(BigInteger.TEN), new Address(BigInteger.ONE), new Uint256(BigInteger.TWO));
        checkEvmLog(EthereumEvent.getEvmLog(new Address("1122334455667788990011223344556677889900"), event1),
                Numeric.hexStringToByteArray("0xf878941122334455667788990011223344556677889900a0000000000000000000000000000000000000000000000000000000000000000aa00000000000000000000000000000000000000000000000000000000000000001a00000000000000000000000000000000000000000000000000000000000000002"));

        MyEvent2 event2 = new MyEvent2(new Address(BigInteger.TEN), new Address(BigInteger.ONE), new Uint256(BigInteger.TWO));
        checkEvmLog(EthereumEvent.getEvmLog(new Address("1122334455667788990011223344556677889900"), event2),
                Numeric.hexStringToByteArray("0xf899941122334455667788990011223344556677889900a038402c0f573a9575ada72aeb0f435fc33c403a134bf3136bb289f2d9ffa334a0a0000000000000000000000000000000000000000000000000000000000000000aa00000000000000000000000000000000000000000000000000000000000000001a00000000000000000000000000000000000000000000000000000000000000002"));

        CaseClassTestEvent1 event3 = new CaseClassTestEvent1(new Address(BigInteger.ONE), new Uint256(BigInteger.TWO));
        checkEvmLog(EthereumEvent.getEvmLog(new Address("1122334455667788990011223344556677889900"), event3),
                Numeric.hexStringToByteArray("0xf857941122334455667788990011223344556677889900a00000000000000000000000000000000000000000000000000000000000000001a00000000000000000000000000000000000000000000000000000000000000002"));

        CaseClassTestEvent2 event4 = new CaseClassTestEvent2(new Address(BigInteger.ONE), new Uint256(BigInteger.TWO));
        checkEvmLog(EthereumEvent.getEvmLog(new Address("1122334455667788990011223344556677889900"), event4),
                Numeric.hexStringToByteArray("0xf857941122334455667788990011223344556677889900a00000000000000000000000000000000000000000000000000000000000000002a00000000000000000000000000000000000000000000000000000000000000001"));

    }

    private void checkEvmLog(EvmLog log, byte[] expected) {
        List<RlpType> list = new ArrayList();
        list.add(RlpString.create(log.address.toBytes()));
        for (var topic : log.topics) {
            list.add(RlpString.create(topic.toBytes()));
        }
        list.add(RlpString.create(log.data));
        assertArrayEquals(RlpEncoder.encode(new RlpList(list)), expected);
    }

    /*
    Example 1:
    https://etherscan.io/tx/0xb55697d90702908180be9efb78ff556e2caffeda17d06d84d21b6ffa9bc86450#eventlog

    Function: Transfer(indexed address, indexed address, uint256)

    {
      "topics": [
        "0xddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef",
        "0x000000000000000000000000c12e077934a3c783d7a42dc5f6d6435ef3d04705",
        "0x00000000000000000000000027239549dd40e1d60f5b80b0c4196923745b1fd2"
      ],
      "blockNumber": "0xe76dbb",
      "transactionHash": "0xb55697d90702908180be9efb78ff556e2caffeda17d06d84d21b6ffa9bc86450",
      "transactionIndex": "0x145",
      "address": "0xc02aaa39b223fe8d0a0e5c4f27ead9083c756cc2",
      "data": "0x000000000000000000000000000000000000000000000000016345785d8a0000",
      "blockHash": "0xdb0aa07aff2008b0eac1e23d12d62b12f000957967667e08165ab6d3e541639e",
      "logIndex": "0x251",
      "removed": false
    }

    Example 2:
    https://etherscan.io/tx/0xb55697d90702908180be9efb78ff556e2caffeda17d06d84d21b6ffa9bc86450#eventlog

    Function: Swapped(address, address, address, address, uint256, uint256)

    {
      "address": "0x11111112542d85b3ef69ae05771c2dccff4faa26",
      "data": "0x000000000000000000000000c12e077934a3c783d7a42dc5f6d6435ef3d04705000000000000000000000000c02aaa39b223fe8d0a0e5c4f27ead9083c756cc2000000000000000000000000eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee000000000000000000000000c12e077934a3c783d7a42dc5f6d6435ef3d04705000000000000000000000000000000000000000000000000016345785d8a0000000000000000000000000000000000000000000000000000016345785d8a0000",
      "blockNumber": "0xe76dbb",
      "transactionIndex": "0x145",
      "blockHash": "0xdb0aa07aff2008b0eac1e23d12d62b12f000957967667e08165ab6d3e541639e",
      "topics": [
        "0xd6d4f5681c246c9f42c203e287975af1601f8df8035a9251f79aab5c8f09e2f8"
      ],
      "transactionHash": "0xb55697d90702908180be9efb78ff556e2caffeda17d06d84d21b6ffa9bc86450",
      "logIndex": "0x253",
      "removed": false
    }

    Example
     */
}
