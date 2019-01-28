package com.horizen.transaction;

import com.horizen.box.BoxUnlocker;
import com.horizen.box.RegularBox;
import com.horizen.proposition.PublicKey25519Proposition;
import com.horizen.secret.PrivateKey25519;
import javafx.util.Pair;
import org.junit.Before;
import org.junit.Test;
import scala.Tuple2;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class RegularTransactionTest {

    long fee;
    long timestamp;
    ArrayList<Pair<RegularBox, PrivateKey25519>> from;
    ArrayList<Pair<PublicKey25519Proposition, Long>> to;

    @Before
    public void BeforeEachTest() {
        fee = 10;
        timestamp = 1547798549470L;
        Tuple2<PrivateKey25519, PublicKey25519Proposition> pair1 = PrivateKey25519.generateKeys("test_seed1".getBytes());
        Tuple2<PrivateKey25519, PublicKey25519Proposition> pair2 = PrivateKey25519.generateKeys("test_seed2".getBytes());
        Tuple2<PrivateKey25519, PublicKey25519Proposition> pair3 = PrivateKey25519.generateKeys("test_seed3".getBytes());

        from = new ArrayList<>();
        from.add(new Pair<>(new RegularBox(pair1._2, 1, 60), pair1._1));
        from.add(new Pair<>(new RegularBox(pair2._2, 1, 50), pair2._1));
        from.add(new Pair<>(new RegularBox(pair3._2, 1, 20), pair3._1));

        Tuple2<PrivateKey25519, PublicKey25519Proposition> pair4 = PrivateKey25519.generateKeys("test_seed4".getBytes());
        Tuple2<PrivateKey25519, PublicKey25519Proposition> pair5 = PrivateKey25519.generateKeys("test_seed5".getBytes());
        Tuple2<PrivateKey25519, PublicKey25519Proposition> pair6 = PrivateKey25519.generateKeys("test_seed6".getBytes());

        to = new ArrayList<>();
        to.add(new Pair<>(pair4._2, 10L));
        to.add(new Pair<>(pair5._2, 20L));
        to.add(new Pair<>(pair6._2, 90L));
    }

    @Test
    public void RegularTransaction_SuccessCreationTest() {
        RegularTransaction transaction = RegularTransaction.create(from, to, fee, timestamp);
        assertEquals("Exception during RegularTransaction creation: fee is different!", fee, transaction.fee());
        assertEquals("Exception during RegularTransaction creation: fee is different!", timestamp, transaction.timestamp());

        List<RegularBox> newBoxes = transaction.newBoxes();
        assertEquals("Exception during RegularTransaction creation: new boxes count is different!", to.size(), newBoxes.size());
        for(int i = 0; i < to.size(); i++) {
            Pair<PublicKey25519Proposition, Long> expected = to.get(i);
            RegularBox actual = newBoxes.get(i);
            assertEquals(String.format("Exception during RegularTransaction creation: new box %d proposition is different!", i), true, expected.getKey().equals(actual.proposition()));
            assertEquals(String.format("Exception during RegularTransaction creation: new box %d value is different!", i), expected.getValue().longValue(), actual.value());
        }

        List<BoxUnlocker<PublicKey25519Proposition>> unlockers = transaction.unlockers();
        assertEquals("Exception during RegularTransaction creation: unlockers count is different!", from.size(), unlockers.size());
        for(int i = 0; i < from.size(); i++) {
            Pair<RegularBox, PrivateKey25519> expected = from.get(i);
            BoxUnlocker<PublicKey25519Proposition> actual = unlockers.get(i);
            assertArrayEquals(String.format("Exception during RegularTransaction creation: unlocker %d box id is different!", i),
                    expected.getKey().id(),actual.closedBoxId());
            assertEquals(String.format("Exception during RegularTransaction creation: unlocker %d proof is invalid!", i),
                    true, actual.boxKey().isValid(expected.getValue().publicImage(), transaction.messageToSign()));
        }
    }

    // TO DO: extend FailureCreationTest with other cases. Look into semantic validity in SimpleBoxTransaction.
    @Test
    public void RegularTransaction_FailureCreationTest() {
        // Test 1: from is null
        boolean exceptionOccurred = false;
        try {
            RegularTransaction.create(null, to, fee, timestamp);
        }
        catch (IllegalArgumentException e) {
            exceptionOccurred = true;
        }
        assertEquals("Test1: Exception during RegularTransaction creation expected", true, exceptionOccurred);

        // Test 2: to is null
        exceptionOccurred = false;
        try {
            RegularTransaction.create(from, null, fee, timestamp);
        }
        catch (IllegalArgumentException e) {
            exceptionOccurred = true;
        }
        assertEquals("Test2: Exception during RegularTransaction creation expected", true, exceptionOccurred);
    }
}