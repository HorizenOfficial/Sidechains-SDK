package com.horizen.transaction;

import com.horizen.box.BoxUnlocker;
import com.horizen.box.RegularBox;
import com.horizen.proposition.PublicKey25519Proposition;
import com.horizen.secret.PrivateKey25519;
import com.horizen.secret.PrivateKey25519Creator;
import com.horizen.utils.Pair;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

public class RegularTransactionTest {

    long fee;
    long timestamp;
    ArrayList<Pair<RegularBox, PrivateKey25519>> from;
    ArrayList<Pair<PublicKey25519Proposition, Long>> to;

    ArrayList<Long> expectedNonces;

    @Before
    public void BeforeEachTest() {
        fee = 10;
        timestamp = 1547798549470L;
        PrivateKey25519Creator creator = PrivateKey25519Creator.getInstance();
        PrivateKey25519 pk1 = creator.generateSecret("test_seed1".getBytes());
        PrivateKey25519 pk2 = creator.generateSecret("test_seed2".getBytes());
        PrivateKey25519 pk3 = creator.generateSecret("test_seed3".getBytes());

        from = new ArrayList<>();
        from.add(new Pair<>(new RegularBox(pk1.publicImage(), 1, 60), pk1));
        from.add(new Pair<>(new RegularBox(pk2.publicImage(), 1, 50), pk2));
        from.add(new Pair<>(new RegularBox(pk3.publicImage(), 1, 20), pk3));

        PrivateKey25519 pk4 = creator.generateSecret("test_seed4".getBytes());
        PrivateKey25519 pk5 = creator.generateSecret("test_seed5".getBytes());
        PrivateKey25519 pk6 = creator.generateSecret("test_seed6".getBytes());

        to = new ArrayList<>();
        to.add(new Pair<>(pk4.publicImage(), 10L));
        to.add(new Pair<>(pk5.publicImage(), 20L));
        to.add(new Pair<>(pk6.publicImage(), 90L));

        expectedNonces = new ArrayList<>(Arrays.asList(
                384769921723993316L,
                -7787981619319975853L,
                1511446075117796171L)
        );
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

        assertEquals("Transaction should be semantically valid.", true, transaction.semanticValidity());
    }

    @Test
    public void newBoxesNonceEnforcingAlgorithmRegressionTest() {
        RegularTransaction transaction = RegularTransaction.create(from, to, fee, timestamp);
        List<RegularBox> newBoxes = transaction.newBoxes();
        for(int i = 0; i < newBoxes.size(); i++)
            assertEquals(String.format("Transaction new box %d has different nonce. Nonce enforcing algorithm is different.", i),
                    expectedNonces.get(i).longValue(), newBoxes.get(i).nonce());
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