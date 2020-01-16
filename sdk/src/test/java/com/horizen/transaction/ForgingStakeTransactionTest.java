package com.horizen.transaction;

import com.horizen.box.BoxUnlocker;
import com.horizen.box.ForgerBox;
import com.horizen.box.NoncedBox;
import com.horizen.box.data.BoxData;
import com.horizen.box.data.ForgerBoxData;
import com.horizen.box.data.RegularBoxData;
import com.horizen.customtypes.CustomBoxData;
import com.horizen.fixtures.BoxFixtureClass;
import com.horizen.proposition.Proposition;
import com.horizen.secret.PrivateKey25519;
import com.horizen.secret.PrivateKey25519Creator;
import com.horizen.utils.Pair;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ForgingStakeTransactionTest extends BoxFixtureClass {

    private long fee;
    private long timestamp;
    private ArrayList<Pair<ForgerBox, PrivateKey25519>> from;
    private ArrayList<BoxData> to;

    private ArrayList<Long> expectedNonces;

    @Before
    public void BeforeEachTest() {
        fee = 10;
        timestamp = 1547798549470L;
        PrivateKey25519Creator creator = PrivateKey25519Creator.getInstance();
        PrivateKey25519 pk1 = creator.generateSecret("fs_test_seed1".getBytes());
        PrivateKey25519 pk2 = creator.generateSecret("fs_test_seed2".getBytes());
        PrivateKey25519 pk3 = creator.generateSecret("fs_test_seed3".getBytes());

        from = new ArrayList<>();
        from.add(new Pair<>(getForgerBox(pk1.publicImage(), 1, 60, pk1.publicImage(), getVRFPublicKey(1L)), pk1));
        from.add(new Pair<>(getForgerBox(pk2.publicImage(), 1, 50, pk2.publicImage(), getVRFPublicKey(2L)), pk2));
        from.add(new Pair<>(getForgerBox(pk3.publicImage(), 1, 20, pk1.publicImage(), getVRFPublicKey(3L)), pk3));

        PrivateKey25519 pk4 = creator.generateSecret("fs_test_seed4".getBytes());
        PrivateKey25519 pk5 = creator.generateSecret("fs_test_seed5".getBytes());
        PrivateKey25519 pk6 = creator.generateSecret("fs_test_seed6".getBytes());

        to = new ArrayList<>();
        to.add(new RegularBoxData(pk4.publicImage(), 10L));
        to.add(new RegularBoxData(pk5.publicImage(), 20L));
        to.add(new ForgerBoxData(pk6.publicImage(), 90L, pk6.publicImage(), getVRFPublicKey(6L)));

        expectedNonces = new ArrayList<>(Arrays.asList(
                4452721337043506109L,
                5853086106862614514L,
                8371587569716231730L)
        );
    }

    @Test
    public void regularBoxTest() {
        ForgingStakeTransaction transaction = ForgingStakeTransaction.create(from, to, fee, timestamp);
        assertEquals("Exception during ForgingStakeTransaction creation: fee is different!", fee, transaction.fee());
        assertEquals("Exception during ForgingStakeTransaction creation: fee is different!", timestamp, transaction.timestamp());

        List<NoncedBox<Proposition>> newBoxes = transaction.newBoxes();
        assertEquals("Exception during ForgingStakeTransaction creation: new boxes count is different!", to.size(), newBoxes.size());
        for(int i = 0; i < to.size(); i++) {
            BoxData expected = to.get(i);
            NoncedBox actual = newBoxes.get(i);
            assertEquals(String.format("Exception during ForgingStakeTransaction creation: new box %d proposition is different!", i), expected.proposition(), actual.proposition());
            assertEquals(String.format("Exception during ForgingStakeTransaction creation: new box %d value is different!", i), expected.value(), actual.value());
        }

        List<BoxUnlocker<Proposition>> unlockers = transaction.unlockers();
        assertEquals("Exception during ForgingStakeTransaction creation: unlockers count is different!", from.size(), unlockers.size());
        for(int i = 0; i < from.size(); i++) {
            Pair<ForgerBox, PrivateKey25519> expected = from.get(i);
            BoxUnlocker<Proposition> actual = unlockers.get(i);
            assertArrayEquals(String.format("Exception during ForgingStakeTransaction creation: unlocker %d box id is different!", i),
                    expected.getKey().id(),actual.closedBoxId());
            assertTrue(String.format("Exception during ForgingStakeTransaction creation: unlocker %d proof is invalid!", i),
                    actual.boxKey().isValid(expected.getValue().publicImage(), transaction.messageToSign()));
        }

        assertTrue("Transaction should be semantically valid.", transaction.semanticValidity());
    }

    @Test
    public void newBoxesNonceEnforcingAlgorithmRegressionTest() {
        ForgingStakeTransaction transaction = ForgingStakeTransaction.create(from, to, fee, timestamp);
        List<NoncedBox<Proposition>> newBoxes = transaction.newBoxes();
        for(int i = 0; i < newBoxes.size(); i++)
            assertEquals(String.format("Transaction new box %d has different nonce. Nonce enforcing algorithm is different.", i),
                    expectedNonces.get(i).longValue(), newBoxes.get(i).nonce());
    }

    @Test
    public void RegularTransaction_FailureCreationTest() {
        // Test 1: from is null
        boolean exceptionOccurred = false;
        try {
            ForgingStakeTransaction.create(null, to, fee, timestamp);
        }
        catch (IllegalArgumentException e) {
            exceptionOccurred = true;
        }
        assertTrue("Test1: Exception during ForgingStakeTransaction creation expected", exceptionOccurred);

        // Test 2: to is null
        exceptionOccurred = false;
        try {
            ForgingStakeTransaction.create(from, null, fee, timestamp);
        }
        catch (IllegalArgumentException e) {
            exceptionOccurred = true;
        }
        assertTrue("Test2: Exception during ForgingStakeTransaction creation expected", exceptionOccurred);

        // Test 3: to contains unsupported box type item - CustomBox
        exceptionOccurred = false;
        to.add(new CustomBoxData(getCustomPrivateKey().publicImage(), 5L));
        try {
            ForgingStakeTransaction.create(from, to, fee, timestamp);
        }
        catch (IllegalArgumentException e) {
            exceptionOccurred = true;
        }
        assertTrue("Test3: Exception during ForgingStakeTransaction creation expected", exceptionOccurred);
    }
}
