package com.horizen.transaction;

import com.horizen.box.*;
import com.horizen.box.data.NoncedBoxData;
import com.horizen.box.data.ForgerBoxData;
import com.horizen.box.data.ZenBoxData;
import com.horizen.box.data.WithdrawalRequestBoxData;
import com.horizen.customtypes.CustomBoxData;
import com.horizen.proposition.MCPublicKeyHashProposition;
import com.horizen.proposition.Proposition;
import com.horizen.proposition.PublicKey25519Proposition;
import com.horizen.secret.PrivateKey25519;
import com.horizen.secret.PrivateKey25519Creator;
import com.horizen.transaction.exception.TransactionSemanticValidityException;
import com.horizen.utils.Pair;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

import com.horizen.fixtures.*;

public class RegularTransactionTest extends BoxFixtureClass {

    long fee;
    ArrayList<Pair<ZenBox, PrivateKey25519>> from;
    ArrayList<NoncedBoxData<? extends Proposition, ? extends Box<? extends Proposition>>> to;

    ArrayList<Long> expectedNonces;

    @Before
    public void BeforeEachTest() {
        fee = 10;
        PrivateKey25519Creator creator = PrivateKey25519Creator.getInstance();
        PrivateKey25519 pk1 = creator.generateSecret("test_seed1".getBytes());
        PrivateKey25519 pk2 = creator.generateSecret("test_seed2".getBytes());
        PrivateKey25519 pk3 = creator.generateSecret("test_seed3".getBytes());

        from = new ArrayList<>();
        from.add(new Pair<>(getZenBox(pk1.publicImage(), 1, 60), pk1));
        from.add(new Pair<>(getZenBox(pk2.publicImage(), 1, 50), pk2));
        from.add(new Pair<>(getZenBox(pk3.publicImage(), 1, 20), pk3));

        PrivateKey25519 pk4 = creator.generateSecret("test_seed4".getBytes());
        PrivateKey25519 pk5 = creator.generateSecret("test_seed5".getBytes());
        PrivateKey25519 pk6 = creator.generateSecret("test_seed6".getBytes());

        to = new ArrayList<>();
        to.add(new ZenBoxData(pk4.publicImage(), 10L));
        to.add(new ZenBoxData(pk5.publicImage(), 20L));
        to.add(new ZenBoxData(pk6.publicImage(), 90L));

        expectedNonces = new ArrayList<>(Arrays.asList(
                -4413338919968165681L,
                -2557784382151979925L,
                -36253851566087546L)
        );
    }

    @Test
    public void zenBoxTest() {
        RegularTransaction transaction = RegularTransaction.create(from, to, fee);
        assertEquals("Exception during RegularTransaction creation: fee is different!", fee, transaction.fee());

        List<Box<Proposition>> newBoxes = transaction.newBoxes();
        assertEquals("Exception during RegularTransaction creation: new boxes count is different!", to.size(), newBoxes.size());
        for(int i = 0; i < to.size(); i++) {
            NoncedBoxData expected = to.get(i);
            Box actual = newBoxes.get(i);
            assertEquals(String.format("Exception during RegularTransaction creation: new box %d proposition is different!", i), expected.proposition(), actual.proposition());
            assertEquals(String.format("Exception during RegularTransaction creation: new box %d value is different!", i), expected.value(), actual.value());
        }

        List<BoxUnlocker<Proposition>> unlockers = transaction.unlockers();
        assertEquals("Exception during RegularTransaction creation: unlockers count is different!", from.size(), unlockers.size());
        for(int i = 0; i < from.size(); i++) {
            Pair<ZenBox, PrivateKey25519> expected = from.get(i);
            BoxUnlocker<Proposition> actual = unlockers.get(i);
            assertArrayEquals(String.format("Exception during RegularTransaction creation: unlocker %d box id is different!", i),
                    expected.getKey().id(),actual.closedBoxId());
            assertTrue(String.format("Exception during RegularTransaction creation: unlocker %d proof is invalid!", i),
                    actual.boxKey().isValid(expected.getValue().publicImage(), transaction.messageToSign()));
        }

        boolean isValid = true;
        try {
            transaction.semanticValidity();
            isValid = true;
        } catch (TransactionSemanticValidityException e) {
            isValid = false;
        } catch (Exception e) {
            fail("TransactionSemanticValidityException type expected.");
        }
        assertTrue("Transaction expected to be semantically valid.", isValid);
    }

    @Test
    public void newBoxesNonceEnforcingAlgorithmRegressionTest() {
        RegularTransaction transaction = RegularTransaction.create(from, to, fee);
        List<Box<Proposition>> newBoxes = transaction.newBoxes();
        for(int i = 0; i < newBoxes.size(); i++){
            assertEquals(String.format("Transaction new box %d has different nonce. Nonce enforcing algorithm is different(%x, %x).", i, expectedNonces.get(i).longValue(), newBoxes.get(i).nonce()),
                    expectedNonces.get(i).longValue(), newBoxes.get(i).nonce());
        }
    }

    // TO DO: extend FailureCreationTest with other cases. Look into semantic validity in SimpleBoxTransaction.
    @Test
    public void RegularTransaction_FailureCreationTest() {
        // Test 1: from is null
        boolean exceptionOccurred = false;
        try {
            RegularTransaction.create(null, to, fee);
        }
        catch (IllegalArgumentException e) {
            exceptionOccurred = true;
        }
        assertTrue("Test1: Exception during RegularTransaction creation expected", exceptionOccurred);

        // Test 2: to is null
        exceptionOccurred = false;
        try {
            RegularTransaction.create(from, null, fee);
        }
        catch (IllegalArgumentException e) {
            exceptionOccurred = true;
        }
        assertTrue("Test2: Exception during RegularTransaction creation expected", exceptionOccurred);

        // Test 3: to contains unsupported box type item - CustomBox
        exceptionOccurred = false;
        to.add(new CustomBoxData(getCustomPrivateKey().publicImage(), 5L));
        try {
            RegularTransaction.create(from, to, fee);
        }
        catch (IllegalArgumentException e) {
            exceptionOccurred = true;
        }
        assertTrue("Test3: Exception during RegularTransaction creation expected", exceptionOccurred);
    }

    @Test
    public void withdrawalRequestTest() {
        // Test 1: Create new transaction with withdrawal requests only
        to.clear();
        to.add(new WithdrawalRequestBoxData(getMCPublicKeyHashProposition(), 70L));
        to.add(new WithdrawalRequestBoxData(getMCPublicKeyHashProposition(), 50L));

        RegularTransaction tx1 = RegularTransaction.create(from, to, fee);

        List<Box<Proposition>> tx1NewBoxes = tx1.newBoxes();

        boolean isValid = true;
        try {
            tx1.semanticValidity();
            isValid = true;
        } catch (TransactionSemanticValidityException e) {
            isValid = false;
        } catch (Exception e) {
            fail("TransactionSemanticValidityException type expected.");
        }
        assertTrue("Transaction expected to be semantically valid.", isValid);

        assertEquals("Count of new boxes must be the same as count of withdrawal requests.",
                to.size(), tx1NewBoxes.size());
        for(Box box : tx1NewBoxes ) {
            assertTrue("Box must be WithdrawalRequestBox", box instanceof WithdrawalRequestBox);
            assertTrue("Transaction must contain new box for specified withdrawal requests data.",
                    to.contains(new WithdrawalRequestBoxData((MCPublicKeyHashProposition)box.proposition(), box.value())));
        }


        // Test 2: Create new transaction with zen boxes and withdrawal requests
        to.clear();
        to.add(new ZenBoxData(getPrivateKey25519().publicImage(), 30L));
        to.add(new ZenBoxData(getPrivateKey25519().publicImage(), 50L));
        to.add(new WithdrawalRequestBoxData(getMCPublicKeyHashProposition(), 10L));
        to.add(new WithdrawalRequestBoxData(getMCPublicKeyHashProposition(), 30L));

        RegularTransaction tx2 = RegularTransaction.create(from, to, fee);

        List<Box<Proposition>> tx2NewBoxes = tx2.newBoxes();

        try {
            tx2.semanticValidity();
            isValid = true;
        } catch (TransactionSemanticValidityException e) {
            isValid = false;
        } catch (Exception e) {
            fail("TransactionSemanticValidityException type expected.");
        }
        assertTrue("Transaction expected to be semantically valid.", isValid);

        assertEquals("Count of new boxes must be the same as count of zen boxes and withdrawal requests.",
                to.size(), tx2NewBoxes.size());
        for(Box box : tx2NewBoxes ) {
            if (box instanceof ZenBox)
                assertTrue("Transaction must contain new box for specified zen boxes data.",
                        to.contains(new ZenBoxData((PublicKey25519Proposition)box.proposition(), box.value())));
            else if (box instanceof WithdrawalRequestBox)
                assertTrue("Transaction must contain new box for specified withdrawal requests data.",
                        to.contains(new WithdrawalRequestBoxData((MCPublicKeyHashProposition)box.proposition(), box.value())));
            else
                fail("Box must be an instance of ZenBox or WithdrawalRequestBox.");
        }
    }

    @Test
    public void forgerBoxTest() {
        // Test 1: Create new transaction with forger boxes only
        to.clear();
        to.add(new ForgerBoxData(getPrivateKey25519().publicImage(), 70L, getPrivateKey25519().publicImage(), getVRFPublicKey()));
        to.add(new ForgerBoxData(getPrivateKey25519().publicImage(), 40L, getPrivateKey25519().publicImage(), getVRFPublicKey()));
        to.add(new ForgerBoxData(getPrivateKey25519().publicImage(), 10L, getPrivateKey25519().publicImage(), getVRFPublicKey()));

        RegularTransaction tx1 = RegularTransaction.create(from, to, fee);

        List<Box<Proposition>> tx1NewBoxes = tx1.newBoxes();

        boolean isValid = true;
        try {
            tx1.semanticValidity();
            isValid = true;
        } catch (TransactionSemanticValidityException e) {
            isValid = false;
        } catch (Exception e) {
            fail("TransactionSemanticValidityException type expected.");
        }
        assertTrue("Transaction expected to be semantically valid.", isValid);

        assertEquals("Count of new boxes must be the same as count of forger boxes.",
                to.size(), tx1NewBoxes.size());
        for(Box box : tx1NewBoxes ) {
            assertTrue("Box must be ForgerBox", box instanceof ForgerBox);
            ForgerBox forgerBox = (ForgerBox)box;
            assertTrue("Transaction must contain new box for specified forger boxes data.",
                    to.contains(new ForgerBoxData(forgerBox.proposition(), forgerBox.value(), forgerBox.blockSignProposition(), forgerBox.vrfPubKey())));
        }


        // Test 2: Create new transaction with zen boxes and forger boxes
        to.clear();
        to.add(new ZenBoxData(getPrivateKey25519().publicImage(), 30L));
        to.add(new ZenBoxData(getPrivateKey25519().publicImage(), 50L));
        to.add(new ForgerBoxData(getPrivateKey25519().publicImage(), 30L, getPrivateKey25519().publicImage(), getVRFPublicKey()));
        to.add(new ForgerBoxData(getPrivateKey25519().publicImage(), 10L, getPrivateKey25519().publicImage(), getVRFPublicKey()));

        RegularTransaction tx2 = RegularTransaction.create(from, to, fee);

        List<Box<Proposition>> tx2NewBoxes = tx2.newBoxes();

        try {
            tx2.semanticValidity();
            isValid = true;
        } catch (TransactionSemanticValidityException e) {
            isValid = false;
        } catch (Exception e) {
            fail("TransactionSemanticValidityException type expected.");
        }
        assertTrue("Transaction expected to be semantically valid.", isValid);

        assertEquals("Count of new boxes must be the same as count of zen boxes and forger boxes.",
                to.size(), tx2NewBoxes.size());
        for(Box box : tx2NewBoxes ) {
            if (box instanceof ZenBox)
                assertTrue("Transaction must contain new box for specified zen boxes data.",
                        to.contains(new ZenBoxData((PublicKey25519Proposition)box.proposition(), box.value())));
            else if (box instanceof ForgerBox) {
                ForgerBox forgerBox = (ForgerBox)box;
                assertTrue("Transaction must contain new box for specified forger boxes data.",
                        to.contains(new ForgerBoxData(forgerBox.proposition(), forgerBox.value(), forgerBox.blockSignProposition(), forgerBox.vrfPubKey())));
            }
            else
                fail("Box must be an instance of ZenBox or ForgerBox.");
        }
    }
}