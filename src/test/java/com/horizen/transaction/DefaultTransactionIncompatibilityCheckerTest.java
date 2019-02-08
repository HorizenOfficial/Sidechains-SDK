package com.horizen.transaction;

import com.horizen.box.RegularBox;
import com.horizen.proposition.PublicKey25519Proposition;
import com.horizen.secret.PrivateKey25519;
import com.horizen.secret.PrivateKey25519Companion;
import javafx.util.Pair;
import org.junit.Test;

import java.util.ArrayList;

import static org.junit.Assert.*;

public class DefaultTransactionIncompatibilityCheckerTest {

    @Test
    public void DefaultTransactionIncompatibilityCheckerTest_IncompatibilityTest() {
        long fee = 10;
        long timestamp = 1547798549470L;
        PrivateKey25519Companion companion = PrivateKey25519Companion.getCompanion();
        PrivateKey25519 pk1 = companion.generateSecret("test_seed1".getBytes());
        PrivateKey25519 pk2 = companion.generateSecret("test_seed2".getBytes());
        PrivateKey25519 pk3 = companion.generateSecret("test_seed3".getBytes());
        PrivateKey25519 pk4 = companion.generateSecret("test_seed4".getBytes());
        PrivateKey25519 pk5 = companion.generateSecret("test_seed5".getBytes());
        PrivateKey25519 pk6 = companion.generateSecret("test_seed6".getBytes());


        // Set inputs for 'newTx'
        ArrayList<Pair<RegularBox, PrivateKey25519>> from1 = new ArrayList<>();
        from1.add(new Pair<>(new RegularBox(pk1.publicImage(), 1, 10), pk1));
        from1.add(new Pair<>(new RegularBox(pk2.publicImage(), 1, 10), pk2));

        // Set inputs for 'currentTx1': compatible to 'nexTx'
        ArrayList<Pair<RegularBox, PrivateKey25519>> from2 = new ArrayList<>();
        from2.add(new Pair<>(new RegularBox(pk3.publicImage(), 1, 10), pk3));
        from2.add(new Pair<>(new RegularBox(pk4.publicImage(), 1, 10), pk4));

        // Set inputs for 'currentTx2': compatible to 'nexTx'
        ArrayList<Pair<RegularBox, PrivateKey25519>> from3 = new ArrayList<>();
        from3.add(new Pair<>(new RegularBox(pk5.publicImage(), 1, 10), pk5));
        from3.add(new Pair<>(new RegularBox(pk6.publicImage(), 1, 10), pk6));

        // Set inputs for 'currentTx3': incompatible to 'nexTx'
        ArrayList<Pair<RegularBox, PrivateKey25519>> from4 = new ArrayList<>();
        from4.add(new Pair<>(new RegularBox(pk1.publicImage(), 1, 10), pk1));
        from4.add(new Pair<>(new RegularBox(pk6.publicImage(), 1, 10), pk6));


        // Set outputs, the same for all transactions
        PrivateKey25519 pk7 = companion.generateSecret("test_seed7".getBytes());
        ArrayList<Pair<PublicKey25519Proposition, Long>> to = new ArrayList<>();
        to.add(new Pair<>(pk7.publicImage(), 10L));

        RegularTransaction newTx = RegularTransaction.create(from1, to, fee, timestamp);
        RegularTransaction currentTx1 = RegularTransaction.create(from2, to, fee, timestamp);
        RegularTransaction currentTx2 = RegularTransaction.create(from3, to, fee, timestamp);
        RegularTransaction currentTx3 = RegularTransaction.create(from4, to, fee, timestamp);

        DefaultTransactionIncompatibilityChecker checker = new DefaultTransactionIncompatibilityChecker();


        // Test 1: test against empty list
        assertEquals("Transaction expected to be compatible to empty list", false, checker.hasIncompatibleTransactions(newTx, new ArrayList<>()));


        // Test 2: test against compatible list
        ArrayList<BoxTransaction> compatibleList = new ArrayList<>();
        compatibleList.add(currentTx1);
        compatibleList.add(currentTx2);
        assertEquals("Transaction expected to be compatible to list", false, checker.hasIncompatibleTransactions(newTx, compatibleList));


        // Test 3: test against incompatible list
        ArrayList<BoxTransaction> incompatibleList = new ArrayList<>();
        incompatibleList.add(currentTx1);
        incompatibleList.add(currentTx3);
        assertEquals("Transaction expected to be incompatible to list", true, checker.hasIncompatibleTransactions(newTx, incompatibleList));
    }
}