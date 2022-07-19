package com.horizen.account.event;

import com.horizen.account.MyEvent3;
import org.junit.Before;
import org.junit.Test;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.generated.Uint256;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.math.BigInteger;

import static org.junit.Assert.assertEquals;

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
        public Address getFrom(){
            return this.from;
        }

        @Parameter(2)
        @Indexed
        public Address getTo(){
            return this.to;
        }

        @Parameter(3)
        public Uint256 getValue(){
            return this.value;
        }
    }

    @Before
    public void BeforeEachTest() {
    }

    @Test
    public void ethereumEventTest() throws ClassNotFoundException, IOException, IllegalAccessException, InvocationTargetException, InstantiationException, NoSuchMethodException {
        MyEvent1 event1 = new MyEvent1(new Address(BigInteger.TEN), new Address(BigInteger.ONE), new Uint256(BigInteger.TWO));
        var expectedLog = "EvmLog (log consensus data) {address=1122334455667788990011223344556677889900, topics=topics{ 000000000000000000000000000000000000000000000000000000000000000a 0000000000000000000000000000000000000000000000000000000000000001}, data=0000000000000000000000000000000000000000000000000000000000000002}";
        assertEquals(EthereumEvent.getEvmLog(new Address("1122334455667788990011223344556677889900"), event1).toString(), expectedLog);

        MyEvent2 event2 = new MyEvent2(new Address(BigInteger.TEN), new Address(BigInteger.ONE), new Uint256(BigInteger.TWO));
        expectedLog = "EvmLog (log consensus data) {address=1122334455667788990011223344556677889900, topics=topics{ 38402c0f573a9575ada72aeb0f435fc33c403a134bf3136bb289f2d9ffa334a0 000000000000000000000000000000000000000000000000000000000000000a 0000000000000000000000000000000000000000000000000000000000000001}, data=0000000000000000000000000000000000000000000000000000000000000002}";
        assertEquals(EthereumEvent.getEvmLog(new Address("1122334455667788990011223344556677889900"), event2).toString(), expectedLog);

        MyEvent3 event3 = new MyEvent3(new Address(BigInteger.ONE), new Uint256(BigInteger.TWO));
        expectedLog = "EvmLog (log consensus data) {address=1122334455667788990011223344556677889900, topics=topics{ 0000000000000000000000000000000000000000000000000000000000000001}, data=0000000000000000000000000000000000000000000000000000000000000002}";
        assertEquals(EthereumEvent.getEvmLog(new Address("1122334455667788990011223344556677889900"), event3).toString(), expectedLog);
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
