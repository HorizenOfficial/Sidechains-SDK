package com.horizen.account.event;

import com.horizen.account.event.annotation.Anonymous;
import com.horizen.account.event.annotation.Indexed;
import com.horizen.account.event.annotation.Parameter;
import com.horizen.evm.interop.EvmLog;
import org.junit.Test;
import org.web3j.abi.TypeEncoder;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Utf8String;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.rlp.RlpEncoder;
import org.web3j.rlp.RlpList;
import org.web3j.rlp.RlpString;
import org.web3j.rlp.RlpType;
import org.web3j.utils.Numeric;
import sparkz.crypto.hash.Keccak256;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class EthereumEventTest {

    // helper because we have a namespace collision here between org.web3j.abi.datatypes.Address and our
    // com.horizen.evm.utils.Address - which is why we need to use the fully qualified name
    private com.horizen.evm.utils.Address evmAddressFromHex(String hex) {
        return new com.horizen.evm.utils.Address(hex);
    }

    @Test
    public void ethereumEventTest() throws ClassNotFoundException, IOException, IllegalAccessException, InvocationTargetException {
        // if not described in the related test comment, the expected result was calculated with an online ABI encoder

        // test addresses
        var addrA = new Address(BigInteger.ONE);
        var addrB = new Address(BigInteger.TEN);
        var value = new Uint256(BigInteger.TWO);

        var contractAddress = evmAddressFromHex("0x1122334455667788990011223344556677889900");

        // Test 1: Test usage of EVM log creation utility with java class, annotated fields, any order
        ClassTestEvent1 event1 = new ClassTestEvent1(addrB, addrA, value);
        checkEvmLog(EthereumEvent.getEvmLog(contractAddress, event1), "0xf899941122334455667788990011223344556677889900a0e08885b895621acf4890c3c582ae58fff9a23a95e827c7128ef8632f2dc74541a0000000000000000000000000000000000000000000000000000000000000000aa00000000000000000000000000000000000000000000000000000000000000001a00000000000000000000000000000000000000000000000000000000000000002");

        // Test 2: Test usage of EVM log creation utility with java class, annotated methods, anonymous class
        ClassTestEvent2 event2 = new ClassTestEvent2(addrB, addrA, value);
        checkEvmLog(EthereumEvent.getEvmLog(contractAddress, event2), "0xf878941122334455667788990011223344556677889900a0000000000000000000000000000000000000000000000000000000000000000aa00000000000000000000000000000000000000000000000000000000000000001a00000000000000000000000000000000000000000000000000000000000000002");

        // Test 3: Test usage of EVM log creation utility without annotations for empty event, but with helpers
        ClassTestEvent3 event3 = new ClassTestEvent3(addrB, addrA, value);
        checkEvmLog(EthereumEvent.getEvmLog(contractAddress, event3), "0xd694112233445566778899001122334455667788990080");

        ClassTestEvent4 event4 = new ClassTestEvent4(addrB, addrA, addrB, addrA, value);
        assertThrows("Test4: Exception expected, because more than four topics defined", IllegalArgumentException.class, () -> EthereumEvent.getEvmLog(contractAddress, event4));

        ClassTestEvent5 event5 = new ClassTestEvent5(addrB, addrA, value);
        assertThrows("Test5: Exception expected, because one access modifier is private", IllegalAccessException.class, () -> EthereumEvent.getEvmLog(contractAddress, event5));

        // Test 6: Compare EVM log creation output with an example from etherscan
        Transfer event6 = new Transfer(
            new Address("0xc12e077934a3c783d7a42dc5f6d6435ef3d04705"),
            new Address("0x27239549dd40e1d60f5b80b0c4196923745b1fd2"),
            new Uint256(Numeric.toBigInt("0x000000000000000000000000000000000000000000000000016345785d8a0000"))
        );
        compareToExample(EthereumEvent.getEvmLog(evmAddressFromHex("0xc02aaa39b223fe8d0a0e5c4f27ead9083c756cc2"), event6));

        // Test 7: Test usage of EVM log creation utility with scala case class, annotated getters
        CaseClassTestEvent1 event7 = new CaseClassTestEvent1(addrA, value);
        checkEvmLog(EthereumEvent.getEvmLog(contractAddress, event7), "0xf878941122334455667788990011223344556677889900a02ec7ce51ad15e07ca73fd7cacb9d67d2f3869c36c815c3dd948ec2814de887c1a00000000000000000000000000000000000000000000000000000000000000001a00000000000000000000000000000000000000000000000000000000000000002");

        // Test 8: Test usage of EVM log creation utility with scala case class, annotated getters
        CaseClassTestEvent2 event8 = new CaseClassTestEvent2(addrA, value);
        checkEvmLog(EthereumEvent.getEvmLog(contractAddress, event8), "0xf878941122334455667788990011223344556677889900a03406a59be3144bab7c05565c85f764c00ef56d2414a74347872befcb0df971d7a00000000000000000000000000000000000000000000000000000000000000002a00000000000000000000000000000000000000000000000000000000000000001");

        // Test 9: Test usage of EVM log creation utility with scala class, annotations for getters
        ScalaClassTestEvent1 event9 = new ScalaClassTestEvent1(addrB, new Uint256(BigInteger.ONE));
        checkEvmLog(EthereumEvent.getEvmLog(contractAddress, event9), "0xf878941122334455667788990011223344556677889900a07796b5efadd2f87e4c76de571ddbcd862cb6bab4d92c875069ea9c9f1b129291a0000000000000000000000000000000000000000000000000000000000000000aa00000000000000000000000000000000000000000000000000000000000000001");

        // Test 10: Test usage of EVM log creation utility scala class without annotations for empty event, but with helpers
        ScalaClassTestEvent2 event10 = new ScalaClassTestEvent2(addrB, new Uint256(BigInteger.ONE));
        checkEvmLog(EthereumEvent.getEvmLog(contractAddress, event10), "0xf7941122334455667788990011223344556677889900a0f827b9d3b22e5344cfdc031a7b699ea1ae1198e15f9a3507d135895f05cf07cb80");

        ScalaClassTestEvent3 event11 = new ScalaClassTestEvent3(0.412F, new Uint256(BigInteger.ONE));
        assertThrows("Test11: Exception expected, because bit size must be 8 bit aligned and in range 0 < bitSize <= 256", IllegalArgumentException.class, () -> EthereumEvent.getEvmLog(evmAddressFromHex("0x1122334455667788990011223344556677889900123123"), event11));

        // Test 12: Test that encoded topics are hashed, if size > 32 byte
        var inputString = new Utf8String("0xf878941122334455667788990011223344556677889900a07796b5efadd2f87e4c76de571ddbcd862cb6bab4d92c875069ea9c9f1b129291a0000000000000000000000000000000000000000000000000000000000000000aa00000000000000000000000000000000000000000000000000000000000000001");
        ScalaClassTestEvent4 event12 = new ScalaClassTestEvent4(inputString, new Uint256(BigInteger.ONE));
        EvmLog event12Log = EthereumEvent.getEvmLog(contractAddress, event12);
        assertArrayEquals((byte[]) Keccak256.hash(Numeric.hexStringToByteArray(TypeEncoder.encode(inputString))), event12Log.topics[1].toBytes());
        checkEvmLog(event12Log, "0xf878941122334455667788990011223344556677889900a0dcb8fdd491a45d818e875a4dc5db01c363eaecb7966131523424f03cc6762e72a062912b66659d157808f00d13d948a2149f644a16d1aaf0468dce8cd47d1d5fc7a00000000000000000000000000000000000000000000000000000000000000001");

        ScalaClassTestEvent5 event13 = new ScalaClassTestEvent5(addrB, new Uint256(BigInteger.ONE));
        assertThrows("Test13: Exception expected, because of duplicate parameter annotation value", IllegalArgumentException.class, () -> EthereumEvent.getEvmLog(contractAddress, event13));

        // Test 14: Test empty event
        ScalaClassTestEvent6 event14 = new ScalaClassTestEvent6();
        checkEvmLog(EthereumEvent.getEvmLog(contractAddress, event14), "0xf7941122334455667788990011223344556677889900a005e96c2eadbc0af661b6b1f3779706d67dbb1aff5c7c0cdd563f462e03ab55b880");

        // Test 15: Test empty anonymous event
        ScalaClassTestEvent7 event15 = new ScalaClassTestEvent7();
        checkEvmLog(EthereumEvent.getEvmLog(contractAddress, event15), "0xd694112233445566778899001122334455667788990080");

        ScalaClassTestEvent8 event16 = new ScalaClassTestEvent8(addrB, new Uint256(BigInteger.ONE));
        assertThrows("Test16: Exception expected, because there are parameter annotations in the constructor (Scala ignores Java @Target)", IllegalArgumentException.class, () -> EthereumEvent.getEvmLog(contractAddress, event16));
    }

    private void checkEvmLog(EvmLog log, String expected) {
        List<RlpType> list = new ArrayList<>();
        list.add(RlpString.create(log.address.toBytes()));
        for (var topic : log.topics) {
            list.add(RlpString.create(topic.toBytes()));
        }
        list.add(RlpString.create(log.data));
        var encoded = Numeric.toHexString(RlpEncoder.encode(new RlpList(list)));
        assertEquals(encoded, expected);
    }

    private void compareToExample(EvmLog log) {
        assertEquals(3, log.topics.length);
        assertArrayEquals(Numeric.hexStringToByteArray("0xddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef"), log.topics[0].toBytes());
        assertArrayEquals(Numeric.hexStringToByteArray("0x000000000000000000000000c12e077934a3c783d7a42dc5f6d6435ef3d04705"), log.topics[1].toBytes());
        assertArrayEquals(Numeric.hexStringToByteArray("0x00000000000000000000000027239549dd40e1d60f5b80b0c4196923745b1fd2"), log.topics[2].toBytes());
        assertArrayEquals(Numeric.hexStringToByteArray("0xc02aaa39b223fe8d0a0e5c4f27ead9083c756cc2"), log.address.toBytes());
        assertArrayEquals(Numeric.hexStringToByteArray("0x000000000000000000000000000000000000000000000000016345785d8a0000"), log.data);
        checkEvmLog(log, "0xf89994c02aaa39b223fe8d0a0e5c4f27ead9083c756cc2a0ddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3efa0000000000000000000000000c12e077934a3c783d7a42dc5f6d6435ef3d04705a000000000000000000000000027239549dd40e1d60f5b80b0c4196923745b1fd2a0000000000000000000000000000000000000000000000000016345785d8a0000");
    }

    private static class ClassTestEvent1 {
        @Indexed
        @Parameter(1)
        public final Address from;

        @Indexed
        @Parameter(3)
        public final Address to;

        @Parameter(2)
        public final Uint256 value;

        public ClassTestEvent1(Address from, Address to, Uint256 value) {
            this.from = from;
            this.to = to;
            this.value = value;
        }
    }

    @Anonymous
    private static class ClassTestEvent2 {
        private final Address from;
        private final Address to;
        private final Uint256 value;

        public ClassTestEvent2(Address from, Address to, Uint256 value) {
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

    @Anonymous
    private static class ClassTestEvent3 {
        private final Address from;
        private final Address to;
        private final Uint256 value;

        public ClassTestEvent3(Address from, Address to, Uint256 value) {
            this.from = from;
            this.to = to;
            this.value = value;
        }

        public Address getFrom() {
            return this.from;
        }

        public Address getTo() {
            return this.to;
        }

        public Uint256 getValue() {
            return this.value;
        }
    }

    private static class ClassTestEvent4 {
        @Indexed
        @Parameter(1)
        public final Address from;
        @Indexed
        @Parameter(2)
        public final Address to;
        @Indexed
        @Parameter(3)
        public final Address oldOwner;
        @Indexed
        @Parameter(4)
        public final Address newOwner;
        @Parameter(5)
        public final Uint256 value;

        public ClassTestEvent4(Address from, Address to, Address oldOwner, Address newOwner, Uint256 value) {
            this.from = from;
            this.to = to;
            this.oldOwner = oldOwner;
            this.newOwner = newOwner;
            this.value = value;
        }
    }

    private static class ClassTestEvent5 {
        @Indexed
        @Parameter(1)
        public final Address from;
        @Parameter(5)
        public final Uint256 value;
        @Indexed
        @Parameter(2)
        private final Address to;

        public ClassTestEvent5(Address from, Address to, Uint256 value) {
            this.from = from;
            this.to = to;
            this.value = value;
        }
    }

    /*
     Example 1: https://etherscan.io/tx/0xb55697d90702908180be9efb78ff556e2caffeda17d06d84d21b6ffa9bc86450#eventlog

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
    */
    private static class Transfer {
        @Indexed
        @Parameter(1)
        public final Address from;
        @Indexed
        @Parameter(2)
        public final Address to;
        @Parameter(3)
        public final Uint256 value;

        private Transfer(Address from, Address to, Uint256 value) {
            this.from = from;
            this.to = to;
            this.value = value;
        }
    }
}
