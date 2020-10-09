package com.horizen.transaction;

import com.horizen.ClosedBoxPositionMocker;
import com.horizen.SidechainTypes;
import com.horizen.block.SidechainBlock;
import com.horizen.box.Box;
import com.horizen.box.NoncedBox;
import com.horizen.box.RegularBox;
import com.horizen.box.data.NoncedBoxData;
import com.horizen.box.data.RegularBoxData;
import com.horizen.fixtures.BoxFixtureClass;
import com.horizen.proposition.Proposition;
import com.horizen.secret.PrivateKey25519;
import com.horizen.secret.PrivateKey25519Creator;
import com.horizen.utils.Pair;
import org.junit.Test;
import org.mockito.MockSettings;
import org.mockito.stubbing.Answer;
import org.mockito.stubbing.Stubber;
import scala.collection.Seq;
import scala.reflect.ClassTag;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

public class DefaultTransactionIncompatibilityCheckerTest extends BoxFixtureClass {

    @Test
    public void DefaultTransactionIncompatibilityCheckerTest_IncompatibilityTest() {
        long fee = 10;
        long timestamp = 1547798549470L;
        PrivateKey25519Creator creator = PrivateKey25519Creator.getInstance();
        PrivateKey25519 pk1 = creator.generateSecret("test_seed1".getBytes());
        PrivateKey25519 pk2 = creator.generateSecret("test_seed2".getBytes());
        PrivateKey25519 pk3 = creator.generateSecret("test_seed3".getBytes());
        PrivateKey25519 pk4 = creator.generateSecret("test_seed4".getBytes());
        PrivateKey25519 pk5 = creator.generateSecret("test_seed5".getBytes());
        PrivateKey25519 pk6 = creator.generateSecret("test_seed6".getBytes());


        // Set inputs for 'newTx'
        ArrayList<Pair<RegularBox, PrivateKey25519>> from1 = new ArrayList<>();
        from1.add(new Pair<>(getRegularBox(pk1.publicImage(), 1, 10), pk1));
        from1.add(new Pair<>(getRegularBox(pk2.publicImage(), 1, 10), pk2));

        // Set inputs for 'currentTx1': compatible to 'nexTx'
        ArrayList<Pair<RegularBox, PrivateKey25519>> from2 = new ArrayList<>();
        from2.add(new Pair<>(getRegularBox(pk3.publicImage(), 1, 10), pk3));
        from2.add(new Pair<>(getRegularBox(pk4.publicImage(), 1, 10), pk4));

        // Set inputs for 'currentTx2': compatible to 'nexTx'
        ArrayList<Pair<RegularBox, PrivateKey25519>> from3 = new ArrayList<>();
        from3.add(new Pair<>(getRegularBox(pk5.publicImage(), 1, 10), pk5));
        from3.add(new Pair<>(getRegularBox(pk6.publicImage(), 1, 10), pk6));

        // Set inputs for 'currentTx3': incompatible to 'nexTx'
        ArrayList<Pair<RegularBox, PrivateKey25519>> from4 = new ArrayList<>();
        from4.add(new Pair<>(getRegularBox(pk1.publicImage(), 1, 10), pk1));
        from4.add(new Pair<>(getRegularBox(pk6.publicImage(), 1, 10), pk6));


        // Set outputs, the same for all transactions
        PrivateKey25519 pk7 = creator.generateSecret("test_seed7".getBytes());
        List<NoncedBoxData<? extends Proposition, ? extends NoncedBox<? extends Proposition>>> to = new ArrayList<>();
        to.add(new RegularBoxData(pk7.publicImage(), 10L));

        RegularTransaction newTx = RegularTransaction.create(from1, to, fee, timestamp);
        RegularTransaction currentTx1 = RegularTransaction.create(from2, to, fee, timestamp);
        RegularTransaction currentTx2 = RegularTransaction.create(from3, to, fee, timestamp);
        RegularTransaction currentTx3 = RegularTransaction.create(from4, to, fee, timestamp);

        DefaultTransactionIncompatibilityChecker checker = new DefaultTransactionIncompatibilityChecker();


        // Test 1: test against empty list
        assertTrue("Transaction expected to be compatible to empty list", checker.isTransactionCompatible(newTx, new ArrayList<>()));


        // Test 2: test against compatible list
        ArrayList<BoxTransaction> compatibleList = new ArrayList<>();
        compatibleList.add(currentTx1);
        compatibleList.add(currentTx2);
        assertTrue("Transaction expected to be compatible to list", checker.isTransactionCompatible(newTx, compatibleList));


        // Test 3: test against incompatible list
        ArrayList<BoxTransaction> incompatibleList = new ArrayList<>();
        incompatibleList.add(currentTx1);
        incompatibleList.add(currentTx3);
        assertFalse("Transaction expected to be incompatible to list", checker.isTransactionCompatible(newTx, incompatibleList));
    }

    @Test
    public void ClosedBoxPositionTest() {
        ClosedBoxPositionMocker mockerCreator = new ClosedBoxPositionMocker();

        BoxTransaction firstTransaction = mockerCreator.createMockedTransactionForIds(Arrays.asList(0L), Arrays.asList(1L, 2L), "1");
        BoxTransaction secondTransaction = mockerCreator.createMockedTransactionForIds(Arrays.asList(10L), Arrays.asList(3L, 4L), "2");
        BoxTransaction thirdTransaction = mockerCreator.createMockedTransactionForIds(Arrays.asList(20L), Arrays.asList(5L, 6L), "3");
        BoxTransaction fourthTransaction = mockerCreator.createMockedTransactionForIds(Arrays.asList(30L), Arrays.asList(7L, 8L), "4");
        BoxTransaction incompatibleWithFirstAndSecondTransaction = mockerCreator.createMockedTransactionForIds(Arrays.asList(0L), Arrays.asList(3L, 2L), "5");

        DefaultTransactionIncompatibilityChecker checker = new DefaultTransactionIncompatibilityChecker();

        assertTrue("Transaction expected to be compatible to empty list", checker.isTransactionCompatible(firstTransaction, new ArrayList<>()));
        assertFalse("Transaction expected to not be compatible", checker.isTransactionCompatible(firstTransaction, Arrays.asList(incompatibleWithFirstAndSecondTransaction)));
        assertFalse("Transaction expected to not be compatible", checker.isTransactionCompatible(incompatibleWithFirstAndSecondTransaction, Arrays.asList(firstTransaction, thirdTransaction)));

        assertTrue("Transaction expected to be compatible", checker.isTransactionCompatible(firstTransaction, Arrays.asList(secondTransaction, thirdTransaction, fourthTransaction)));
        assertTrue("Transaction expected to be compatible", checker.isTransactionCompatible(fourthTransaction, Arrays.asList(firstTransaction, secondTransaction, thirdTransaction)));

        assertFalse("Transaction expected to not be compatible", checker.isTransactionCompatible(thirdTransaction, Arrays.asList(thirdTransaction)));
    }
}