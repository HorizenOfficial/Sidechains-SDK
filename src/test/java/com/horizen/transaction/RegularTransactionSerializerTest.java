package com.horizen.transaction;

import com.horizen.box.RegularBox;
import com.horizen.proposition.PublicKey25519Proposition;
import com.horizen.secret.PrivateKey25519;
import javafx.util.Pair;
import org.junit.Before;
import org.junit.Test;
import scala.Tuple2;
import scala.util.Try;

import java.util.ArrayList;

import static org.junit.Assert.*;

public class RegularTransactionSerializerTest {
    RegularTransaction transaction;

    @Before
    public void beforeEachTest() {
        long fee = 10;
        long timestamp = 1547798549470L;
        Tuple2<PrivateKey25519, PublicKey25519Proposition> pair1 = PrivateKey25519.generateKeys("test_seed1".getBytes());
        Tuple2<PrivateKey25519, PublicKey25519Proposition> pair2 = PrivateKey25519.generateKeys("test_seed2".getBytes());
        Tuple2<PrivateKey25519, PublicKey25519Proposition> pair3 = PrivateKey25519.generateKeys("test_seed3".getBytes());

        ArrayList<Pair<RegularBox, PrivateKey25519>> from = new ArrayList<>();
        from.add(new Pair<>(new RegularBox(pair1._2, 1, 60), pair1._1));
        from.add(new Pair<>(new RegularBox(pair2._2, 1, 50), pair2._1));
        from.add(new Pair<>(new RegularBox(pair3._2, 1, 20), pair3._1));

        Tuple2<PrivateKey25519, PublicKey25519Proposition> pair4 = PrivateKey25519.generateKeys("test_seed4".getBytes());
        Tuple2<PrivateKey25519, PublicKey25519Proposition> pair5 = PrivateKey25519.generateKeys("test_seed5".getBytes());
        Tuple2<PrivateKey25519, PublicKey25519Proposition> pair6 = PrivateKey25519.generateKeys("test_seed6".getBytes());

        ArrayList<Pair<PublicKey25519Proposition, Long>> to = new ArrayList<>();
        to.add(new Pair<>(pair4._2, 10L));
        to.add(new Pair<>(pair5._2, 20L));
        to.add(new Pair<>(pair6._2, 90L));

        transaction = RegularTransaction.create(from, to, fee, timestamp);
    }

    @Test
    public void RegularTransactionSerializerTest_SerializationTest() {
        TransactionSerializer serializer = transaction.serializer();
        byte[] bytes = serializer.toBytes(transaction);

        Try<RegularTransaction> t = serializer.parseBytes(bytes);
        assertEquals("Transaction serialization failed.", true, t.isSuccess());
        assertEquals("Deserialized transactions expected to be equal", true, transaction.id().equals(t.get().id()));

        boolean failureExpected = serializer.parseBytes("broken bytes".getBytes()).isFailure();
        assertEquals("Failure during parsing expected", true, failureExpected);

    }
}