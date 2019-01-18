package com.horizen.transaction;

import com.horizen.box.Box;
import com.horizen.box.RegularBox;
import com.horizen.node.NodeWallet;
import com.horizen.proposition.ProofOfKnowledgeProposition;
import com.horizen.proposition.PublicKey25519Proposition;
import com.horizen.secret.PrivateKey25519;
import com.horizen.secret.Secret;
import javafx.util.Pair;
import org.junit.Before;
import org.junit.Test;
import scala.Tuple2;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

class RegularTransactionCreatorNodeWallet implements NodeWallet {

    List<Pair<Box, Long>> _boxesWithCreationTime;
    List<Secret> _secrets;
    public RegularTransactionCreatorNodeWallet(List<Pair<Box, Long>> boxesWithCreationTime, List<Secret> secrets) {
        _boxesWithCreationTime = boxesWithCreationTime;
        _secrets = secrets;
    }

    @Override
    public List<Pair<Box, Long>> boxesWithCreationTime() {
        return _boxesWithCreationTime;
    }

    @Override
    public Secret secretByPublicImage(ProofOfKnowledgeProposition publicImage) {
        for(Secret s : _secrets)
        {
            if(s.publicImage().equals(publicImage))
                return s;
        }
        return null;
    }
}

public class RegularTransactionCreatorTest {

    Tuple2<PrivateKey25519, PublicKey25519Proposition> pair1;
    Tuple2<PrivateKey25519, PublicKey25519Proposition> pair2;
    Tuple2<PrivateKey25519, PublicKey25519Proposition> pair3;
    Tuple2<PrivateKey25519, PublicKey25519Proposition> pair4;
    Tuple2<PrivateKey25519, PublicKey25519Proposition> pair5;
    Tuple2<PrivateKey25519, PublicKey25519Proposition> pair6;

    NodeWallet defaultWallet;

    @Before
    public void beforeEachTest() {
        pair1 = PrivateKey25519.generateKeys("test_seed1".getBytes());
        pair2 = PrivateKey25519.generateKeys("test_seed2".getBytes());
        pair3 = PrivateKey25519.generateKeys("test_seed3".getBytes());
        pair4 = PrivateKey25519.generateKeys("test_seed4".getBytes());
        pair5 = PrivateKey25519.generateKeys("test_seed5".getBytes());
        pair6 = PrivateKey25519.generateKeys("test_seed6".getBytes());

        List<Pair<Box, Long>> boxesWithCreationTime = new ArrayList<>();
        boxesWithCreationTime.add(new Pair<>(new RegularBox(pair1._2, 1, 30), 1000L));
        boxesWithCreationTime.add(new Pair<>(new RegularBox(pair2._2, 1, 40), 2000L));
        boxesWithCreationTime.add(new Pair<>(new RegularBox(pair3._2, 1, 50), 3000L));

        List<Secret> secrets = new ArrayList<>();
        secrets.add(pair1._1);
        secrets.add(pair2._1);
        secrets.add(pair3._1);

        defaultWallet = new RegularTransactionCreatorNodeWallet(boxesWithCreationTime, secrets);
    }

    @Test
    public void RegularTransactionCreator_SuccessCreationTest() {
        List<Pair<PublicKey25519Proposition, Long>> to = new ArrayList<>();
        to.add( new Pair<>(pair4._2, 20L));
        to.add( new Pair<>(pair5._2, 30L));
        RegularTransaction transaction = RegularTransactionCreator.create(defaultWallet, to, pair6._2, 10, new ArrayList<byte[]>());

        assertEquals("RegularTransactionCreator: change expected.", 3, transaction.newBoxes().size());
        assertEquals("RegularTransactionCreator: 2 inputs expected.", 2, transaction.unlockers().size());
    }

    @Test
    public void RegularTransactionCreator_FeeTest() {
        List<Pair<Box, Long>> boxesWithCreationTime = new ArrayList<>();
        boxesWithCreationTime.add(new Pair<>(new RegularBox(pair1._2, 1, 10), 1000L));
        boxesWithCreationTime.add(new Pair<>(new RegularBox(pair2._2, 1, 20), 2000L));
        boxesWithCreationTime.add(new Pair<>(new RegularBox(pair3._2, 1, 30), 3000L));

        List<Secret> secrets = new ArrayList<>();
        secrets.add(pair1._1);
        secrets.add(pair2._1);
        secrets.add(pair3._1);

        NodeWallet wallet = new RegularTransactionCreatorNodeWallet(boxesWithCreationTime, secrets);

        List<Pair<PublicKey25519Proposition, Long>> to = new ArrayList<>();
        to.add( new Pair<>(pair4._2, 10L));

        // Note: total 'from' value is 60, total 'to' value is 10

        // Test 1: fee > total_from - total_to
        long fee = 100L;
        boolean exceptionOccurred = false;
        try {
            RegularTransaction transaction = RegularTransactionCreator.create(wallet, to, pair5._2, fee, new ArrayList<byte[]>());
        }
        catch (IllegalArgumentException e) {
            exceptionOccurred = true;
        }
        assertEquals("Test1: Exception expected: fee is to big", true, exceptionOccurred);


        // Test 2: fee = total_from - total_to
        fee = 50L;
        exceptionOccurred = false;
        try {
            RegularTransaction transaction = RegularTransactionCreator.create(wallet, to, pair5._2, fee, new ArrayList<byte[]>());
        }
        catch (IllegalArgumentException e) {
            exceptionOccurred = true;
        }
        assertEquals("Test2: Creation expected: fee is OK", false, exceptionOccurred);


        // Test 3: fee < total_from - total_to
        fee = 50L;
        exceptionOccurred = false;
        try {
            RegularTransaction transaction = RegularTransactionCreator.create(wallet, to, pair5._2, fee, new ArrayList<byte[]>());
        }
        catch (IllegalArgumentException e) {
            exceptionOccurred = true;
        }
        assertEquals("Test3: Creation expected: fee is OK", false, exceptionOccurred);
    }

    @Test
    public void RegularTransactionCreator_ChangeTest() {
        List<Pair<Box, Long>> boxesWithCreationTime = new ArrayList<>();
        boxesWithCreationTime.add(new Pair<>(new RegularBox(pair1._2, 1, 10), 1000L));
        boxesWithCreationTime.add(new Pair<>(new RegularBox(pair2._2, 1, 20), 2000L));
        boxesWithCreationTime.add(new Pair<>(new RegularBox(pair3._2, 1, 30), 3000L));

        List<Secret> secrets = new ArrayList<>();
        secrets.add(pair1._1);
        secrets.add(pair2._1);
        secrets.add(pair3._1);

        NodeWallet wallet = new RegularTransactionCreatorNodeWallet(boxesWithCreationTime, secrets);

        List<Pair<PublicKey25519Proposition, Long>> to = new ArrayList<>();
        to.add( new Pair<>(pair4._2, 10L));

        // Note: total 'from' value is 60, total 'to' value is 10
        PublicKey25519Proposition changeAddress = pair5._2;

        // Test 1: fee = total_from - total_to -> no change occurrence in newBoxes()
        boolean occurrenceExpected = false;
        long fee = 50l;

        RegularTransaction transaction = RegularTransactionCreator.create(wallet, to, changeAddress, fee, new ArrayList<byte[]>());

        List<RegularBox> boxes = transaction.newBoxes();
        for(RegularBox box : boxes)
            if(box.proposition().equals(changeAddress))
                occurrenceExpected = true;

        assertEquals("Test1: Box with change is NOT expected", false, occurrenceExpected);


        // Test 2: fee < total_from - total_to -> change occurrence expected in newBoxes() and equal to 10
        occurrenceExpected = false;
        fee = 40l;
        transaction = RegularTransactionCreator.create(wallet, to, changeAddress, fee, new ArrayList<byte[]>());

        boxes = transaction.newBoxes();
        for(RegularBox box : boxes)
            if(box.proposition().equals(changeAddress)) {
                occurrenceExpected = true;
                assertEquals("Test2: Box with change has different value", 10, box.value());
            }

        assertEquals("Test1: Box with change is expected", true, occurrenceExpected);
    }

    @Test
    public void RegularTransactionCreator_OutputsTest() {
        List<Pair<Box, Long>> boxesWithCreationTime = new ArrayList<>();
        boxesWithCreationTime.add(new Pair<>(new RegularBox(pair1._2, 1, 10), 1000L));
        boxesWithCreationTime.add(new Pair<>(new RegularBox(pair2._2, 1, 20), 2000L));
        boxesWithCreationTime.add(new Pair<>(new RegularBox(pair3._2, 1, 30), 3000L));

        List<Secret> secrets = new ArrayList<>();
        secrets.add(pair1._1);
        secrets.add(pair2._1);
        secrets.add(pair3._1);

        NodeWallet wallet = new RegularTransactionCreatorNodeWallet(boxesWithCreationTime, secrets);
        PublicKey25519Proposition changeAddress = pair6._2;


        // Test 1: empty 'to' list
        // total 'from' value is 60, total 'to' value is 0
        long fee = 60l;
        RegularTransaction transaction = RegularTransactionCreator.create(wallet, new ArrayList<>(), changeAddress, fee, new ArrayList<byte[]>());
        assertEquals("Test1: Boxes list expected to be empty", 0, transaction.newBoxes().size());


        // Test 2: NOT empty 'to' list
        List<Pair<PublicKey25519Proposition, Long>> to = new ArrayList<>();
        to.add( new Pair<>(pair4._2, 10L));
        to.add( new Pair<>(pair5._2, 20L));
        fee = 30L;
        // total 'from' value is 60, total 'to' value is 30
        transaction = RegularTransactionCreator.create(wallet, to, changeAddress, fee, new ArrayList<byte[]>());
        assertEquals("Test2: Boxes list size must be equal to 2", 2, transaction.newBoxes().size());
    }

    @Test
    public void RegularTransactionCreator_BoxIdsToExcludeTest() {
        List<Pair<Box, Long>> boxesWithCreationTime = new ArrayList<>();
        RegularBox boxToExclude = new RegularBox(pair1._2, 1, 100);
        boxesWithCreationTime.add(new Pair<>(boxToExclude, 1000L));

        List<Secret> secrets = new ArrayList<>();
        secrets.add(pair1._1);

        NodeWallet wallet = new RegularTransactionCreatorNodeWallet(boxesWithCreationTime, secrets);

        List<Pair<PublicKey25519Proposition, Long>> to = new ArrayList<>();
        to.add( new Pair<>(pair2._2, 50L));

        // Note: total 'from' value is 100, total 'to' value is 10
        PublicKey25519Proposition changeAddress = pair5._2;

        // Test 1: exclude pair1 key -> now suitable keys in wallet
        boolean exceptionOccurred = false;
        long fee = 10l;

        ArrayList<byte[]> boxIdsToExclude = new ArrayList<>();
        boxIdsToExclude.add(boxToExclude.id());
        try {
            RegularTransactionCreator.create(wallet, to, changeAddress, fee, boxIdsToExclude);
        }
        catch (IllegalArgumentException e) {
            exceptionOccurred = true;
        }
        assertEquals("Exception expected: key in wallet must be ignored", true, exceptionOccurred);
    }

    @Test
    public void RegularTransactionCreator_InputsOrderTrst() {
        List<Pair<Box, Long>> boxesWithCreationTime = new ArrayList<>();
        RegularBox expectedBox = new RegularBox(pair3._2, 1, 10);
        boxesWithCreationTime.add(new Pair<>(new RegularBox(pair1._2, 1, 10), 3L));
        boxesWithCreationTime.add(new Pair<>(expectedBox, 1L));
        boxesWithCreationTime.add(new Pair<>(new RegularBox(pair2._2, 1, 10), 2L));

        List<Secret> secrets = new ArrayList<>();
        secrets.add(pair1._1);
        secrets.add(pair2._1);
        secrets.add(pair3._1);

        NodeWallet wallet = new RegularTransactionCreatorNodeWallet(boxesWithCreationTime, secrets);

        List<Pair<PublicKey25519Proposition, Long>> to = new ArrayList<>();
        to.add( new Pair<>(pair4._2, 10L));
        long fee = 0L;

        RegularTransaction transaction = RegularTransactionCreator.create(wallet, to, pair5._2, fee, new ArrayList<byte[]>());
        assertEquals("Only one box unlocker expected.", 1, transaction.unlockers().size());
        assertArrayEquals("Another box expected.", expectedBox.id(), transaction.unlockers().get(0).closedBoxId());
    }

    @Test
    public void RegularTransactionCreator_NullArgumentTest() {
        List<Pair<PublicKey25519Proposition, Long>> to = new ArrayList<>();
        to.add( new Pair<>(pair4._2, 20L));
        to.add( new Pair<>(pair5._2, 30L));


        // Test 1
        boolean exceptionOccurred = false;
        try {
            RegularTransaction transaction = RegularTransactionCreator.create(null, to, pair6._2, 10, new ArrayList<byte[]>());
        }
        catch (IllegalArgumentException e) {
            exceptionOccurred = true;
        }
        assertEquals("Test1: Exception expected: wallet is null", true, exceptionOccurred);


        // Test 2
        exceptionOccurred = false;
        try {
            RegularTransaction transaction = RegularTransactionCreator.create(defaultWallet, null, pair6._2, 10, new ArrayList<byte[]>());
        }
        catch (IllegalArgumentException e) {
            exceptionOccurred = true;
        }
        assertEquals("Test2: Exception expected: 'to' is null", true, exceptionOccurred);


        // Test 3
        exceptionOccurred = false;
        try {
            RegularTransaction transaction = RegularTransactionCreator.create(defaultWallet, to, null, 10, new ArrayList<byte[]>());
        }
        catch (IllegalArgumentException e) {
            exceptionOccurred = true;
        }
        assertEquals("Test3: Exception expected: change address is null", true, exceptionOccurred);


        // Test 4
        exceptionOccurred = false;
        try {
            RegularTransaction transaction = RegularTransactionCreator.create(defaultWallet, to, pair6._2, 10, null);
        }
        catch (IllegalArgumentException e) {
            exceptionOccurred = true;
        }
        assertEquals("Test4: Exception expected: boxIdsToExclude is null", true, exceptionOccurred);
    }
}