package com.horizen.transaction;

import com.horizen.box.Box;
import com.horizen.box.NoncedBox;
import com.horizen.box.RegularBox;
import com.horizen.node.NodeWallet;
import com.horizen.proposition.MCPublicKeyHash;
import com.horizen.proposition.Proposition;
import com.horizen.proposition.PublicKey25519Proposition;
import com.horizen.secret.PrivateKey25519;
import com.horizen.secret.PrivateKey25519Creator;
import com.horizen.secret.Secret;
import javafx.util.Pair;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.*;

class TransactionCreatorNodeWallet implements NodeWallet {

    List<Box<Proposition>> _boxesOrderedBytCreationTime;
    List<Secret> _secrets;
    public TransactionCreatorNodeWallet(List<Pair<Box, Long>> boxesWithCreationTime, List<Secret> secrets) {
        List<Pair<Box, Long>> _boxesWithCreationTime = new ArrayList<>(boxesWithCreationTime);
        _boxesWithCreationTime.sort( (a, b) ->  Long.signum (a.getValue() - b.getValue()));
        _boxesOrderedBytCreationTime = new ArrayList<>();
        for(Pair<Box, Long> walletBox : _boxesWithCreationTime)
            _boxesOrderedBytCreationTime.add(walletBox.getKey());
        _secrets = secrets;
    }

    @Override
    public Long allBoxesBalance() {
        long sum = 0;
        for(Box b: _boxesOrderedBytCreationTime){
            sum += b.value();
        }
        return sum;
    }

    @Override
    public List<Box<Proposition>> allBoxes() {
        return _boxesOrderedBytCreationTime;
    }

    @Override
    public List<Box<Proposition>> allBoxes(List<byte[]> boxIdsToExclude) {
        List<Box<Proposition>> filteredBoxes = new ArrayList<>();
        for(Box box : _boxesOrderedBytCreationTime) {
            boolean acceptable = true;
            for(byte[] idToExclude : boxIdsToExclude)
                if(Arrays.equals(box.id(), idToExclude)) {
                    acceptable = false;
                    break;
                }
            if(acceptable)
                filteredBoxes.add(box);
        }
        return filteredBoxes;
    }

    @Override
    public List<Box<Proposition>> boxesOfType(Class<? extends Box<? extends Proposition>> type) {
        List<Box<Proposition>> filteredBoxes = new ArrayList<>();
        for(Box box : _boxesOrderedBytCreationTime) {
            if(box.getClass().equals(type))
                filteredBoxes.add(box);
        }
        return filteredBoxes;
    }

    @Override
    public List<Box<Proposition>> boxesOfType(Class<? extends Box<? extends Proposition>> type, List<byte[]> boxIdsToExclude) {
        List<Box<Proposition>> filteredBoxes = new ArrayList<>();
        for(Box box : _boxesOrderedBytCreationTime) {
            if(!box.getClass().equals(type))
                continue;
            boolean acceptable = true;
            for(byte[] idToExclude : boxIdsToExclude)
                if(Arrays.equals(box.id(), idToExclude)) {
                    acceptable = false;
                    break;
                }
            if(acceptable)
                filteredBoxes.add(box);
        }
        return filteredBoxes;
    }

    //TODO Implement
    @Override
    public Long boxesBalance(Class<? extends Box<? extends Proposition>> type) {
        return 0L;
    }

    @Override
    public Optional<Secret> secretByPublicKey(Proposition publicImage) {
        for(Secret s : _secrets)
        {
            if(s.publicImage().equals(publicImage))
                return Optional.ofNullable(s);
        }
        return Optional.empty();
    }

    @Override
    public List<Secret> allSecrets() {
        return null;
    }

    @Override
    public List<Secret> secretsOfType(Class<? extends Secret> type) {
        return null;
    }

    @Override
    public byte[] walletSeed() {
        return "seed".getBytes();
    }
}

public class RegularTransactionCreatorTest {

    PrivateKey25519 pk1;
    PrivateKey25519 pk2;
    PrivateKey25519 pk3;
    PrivateKey25519 pk4;
    PrivateKey25519 pk5;
    PrivateKey25519 pk6;

    NodeWallet defaultWallet;

    @Before
    public void beforeEachTest() {
        PrivateKey25519Creator creator = PrivateKey25519Creator.getInstance();
        pk1 = creator.generateSecret("test_seed1".getBytes());
        pk2 = creator.generateSecret("test_seed2".getBytes());
        pk3 = creator.generateSecret("test_seed3".getBytes());
        pk4 = creator.generateSecret("test_seed4".getBytes());
        pk5 = creator.generateSecret("test_seed5".getBytes());
        pk6 = creator.generateSecret("test_seed6".getBytes());

        List<Pair<Box, Long>> boxesWithCreationTime = new ArrayList<>();
        boxesWithCreationTime.add(new Pair<>(new RegularBox(pk1.publicImage(), 1, 30), 1000L));
        boxesWithCreationTime.add(new Pair<>(new RegularBox(pk2.publicImage(), 1, 40), 2000L));
        boxesWithCreationTime.add(new Pair<>(new RegularBox(pk3.publicImage(), 1, 50), 3000L));

        List<Secret> secrets = new ArrayList<>();
        secrets.add(pk1);
        secrets.add(pk2);
        secrets.add(pk3);

        defaultWallet = new TransactionCreatorNodeWallet(boxesWithCreationTime, secrets);
    }

    @Test
    public void RegularTransactionCreator_SuccessCreationTest() {
        List<Pair<PublicKey25519Proposition, Long>> to = new ArrayList<>();
        List<Pair<MCPublicKeyHash, Long>> withdrawalRequests = new ArrayList<>();

        to.add( new Pair<>(pk4.publicImage(), 20L));
        to.add( new Pair<>(pk5.publicImage(), 30L));
        RegularTransaction transaction = RegularTransactionCreator.create(defaultWallet, to, withdrawalRequests, pk6.publicImage(), 10, new ArrayList<byte[]>());

        assertEquals("RegularTransactionCreator: change expected.", 3, transaction.newBoxes().size());
        assertEquals("RegularTransactionCreator: 2 inputs expected.", 2, transaction.unlockers().size());
    }

    @Test
    public void RegularTransactionCreator_FeeTest() {
        List<Pair<Box, Long>> boxesWithCreationTime = new ArrayList<>();
        boxesWithCreationTime.add(new Pair<>(new RegularBox(pk1.publicImage(), 1, 10), 1000L));
        boxesWithCreationTime.add(new Pair<>(new RegularBox(pk2.publicImage(), 1, 20), 2000L));
        boxesWithCreationTime.add(new Pair<>(new RegularBox(pk3.publicImage(), 1, 30), 3000L));

        List<Secret> secrets = new ArrayList<>();
        secrets.add(pk1);
        secrets.add(pk2);
        secrets.add(pk3);

        NodeWallet wallet = new TransactionCreatorNodeWallet(boxesWithCreationTime, secrets);

        List<Pair<PublicKey25519Proposition, Long>> to = new ArrayList<>();
        to.add( new Pair<>(pk4.publicImage(), 10L));

        List<Pair<MCPublicKeyHash, Long>> withdrawalRequests = new ArrayList<>();

        // Note: total 'from' value is 60, total 'to' value is 10

        // Test 1: fee > total_from - total_to
        long fee = 100L;
        boolean exceptionOccurred = false;
        try {
            RegularTransaction transaction = RegularTransactionCreator.create(wallet, to, withdrawalRequests, pk5.publicImage(), fee, new ArrayList<byte[]>());
        }
        catch (IllegalArgumentException e) {
            exceptionOccurred = true;
        }
        assertEquals("Test1: Exception expected: fee is to big", true, exceptionOccurred);


        // Test 2: fee = total_from - total_to
        fee = 50L;
        exceptionOccurred = false;
        try {
            RegularTransaction transaction = RegularTransactionCreator.create(wallet, to, withdrawalRequests, pk5.publicImage(), fee, new ArrayList<byte[]>());
        }
        catch (IllegalArgumentException e) {
            exceptionOccurred = true;
        }
        assertEquals("Test2: Creation expected: fee is OK", false, exceptionOccurred);


        // Test 3: fee < total_from - total_to
        fee = 50L;
        exceptionOccurred = false;
        try {
            RegularTransaction transaction = RegularTransactionCreator.create(wallet, to, withdrawalRequests, pk5.publicImage(), fee, new ArrayList<byte[]>());
        }
        catch (IllegalArgumentException e) {
            exceptionOccurred = true;
        }
        assertEquals("Test3: Creation expected: fee is OK", false, exceptionOccurred);
    }

    @Test
    public void RegularTransactionCreator_ChangeTest() {
        List<Pair<Box, Long>> boxesWithCreationTime = new ArrayList<>();
        boxesWithCreationTime.add(new Pair<>(new RegularBox(pk1.publicImage(), 1, 10), 1000L));
        boxesWithCreationTime.add(new Pair<>(new RegularBox(pk2.publicImage(), 1, 20), 2000L));
        boxesWithCreationTime.add(new Pair<>(new RegularBox(pk3.publicImage(), 1, 30), 3000L));

        List<Secret> secrets = new ArrayList<>();
        secrets.add(pk1);
        secrets.add(pk2);
        secrets.add(pk3);

        NodeWallet wallet = new TransactionCreatorNodeWallet(boxesWithCreationTime, secrets);

        List<Pair<PublicKey25519Proposition, Long>> to = new ArrayList<>();
        to.add( new Pair<>(pk4.publicImage(), 10L));

        List<Pair<MCPublicKeyHash, Long>> withdrawalRequests = new ArrayList<>();

        // Note: total 'from' value is 60, total 'to' value is 10
        PublicKey25519Proposition changeAddress = pk5.publicImage();

        // Test 1: fee = total_from - total_to -> no change occurrence in newBoxes()
        boolean occurrenceExpected = false;
        long fee = 50l;

        RegularTransaction transaction = RegularTransactionCreator.create(wallet, to, withdrawalRequests, changeAddress, fee, new ArrayList<byte[]>());

        List<NoncedBox<Proposition>> boxes = transaction.newBoxes();
        for(NoncedBox box : boxes)
            if(box.proposition().equals(changeAddress))
                occurrenceExpected = true;

        assertEquals("Test1: Box with change is NOT expected", false, occurrenceExpected);


        // Test 2: fee < total_from - total_to -> change occurrence expected in newBoxes() and equal to 10
        occurrenceExpected = false;
        fee = 40l;
        transaction = RegularTransactionCreator.create(wallet, to, withdrawalRequests, changeAddress, fee, new ArrayList<byte[]>());

        boxes = transaction.newBoxes();
        for(NoncedBox box : boxes)
            if(box.proposition().equals(changeAddress)) {
                occurrenceExpected = true;
                assertEquals("Test2: Box with change has different value", 10, box.value());
            }

        assertEquals("Test1: Box with change is expected", true, occurrenceExpected);
    }

    @Test
    public void RegularTransactionCreator_OutputsTest() {
        List<Pair<Box, Long>> boxesWithCreationTime = new ArrayList<>();
        boxesWithCreationTime.add(new Pair<>(new RegularBox(pk1.publicImage(), 1, 10), 1000L));
        boxesWithCreationTime.add(new Pair<>(new RegularBox(pk2.publicImage(), 1, 20), 2000L));
        boxesWithCreationTime.add(new Pair<>(new RegularBox(pk3.publicImage(), 1, 30), 3000L));

        List<Secret> secrets = new ArrayList<>();
        secrets.add(pk1);
        secrets.add(pk2);
        secrets.add(pk3);

        NodeWallet wallet = new TransactionCreatorNodeWallet(boxesWithCreationTime, secrets);
        PublicKey25519Proposition changeAddress = pk6.publicImage();


        // Test 1: empty 'to' list
        // total 'from' value is 60, total 'to' value is 0
        long fee = 60l;
        RegularTransaction transaction = RegularTransactionCreator.create(wallet, new ArrayList<>(), new ArrayList<>(), changeAddress, fee, new ArrayList<byte[]>());
        assertEquals("Test1: Boxes list expected to be empty", 0, transaction.newBoxes().size());


        // Test 2: NOT empty 'to' list
        List<Pair<PublicKey25519Proposition, Long>> to = new ArrayList<>();
        to.add( new Pair<>(pk4.publicImage(), 10L));
        to.add( new Pair<>(pk5.publicImage(), 20L));
        fee = 30L;
        // total 'from' value is 60, total 'to' value is 30
        transaction = RegularTransactionCreator.create(wallet, to, new ArrayList<>(), changeAddress, fee, new ArrayList<byte[]>());
        assertEquals("Test2: Boxes list size must be equal to 2", 2, transaction.newBoxes().size());
    }

    @Test
    public void RegularTransactionCreator_BoxIdsToExcludeTest() {
        List<Pair<Box, Long>> boxesWithCreationTime = new ArrayList<>();
        RegularBox boxToExclude = new RegularBox(pk1.publicImage(), 1, 100);
        boxesWithCreationTime.add(new Pair<>(boxToExclude, 1000L));

        List<Secret> secrets = new ArrayList<>();
        secrets.add(pk1);

        NodeWallet wallet = new TransactionCreatorNodeWallet(boxesWithCreationTime, secrets);

        List<Pair<PublicKey25519Proposition, Long>> to = new ArrayList<>();
        to.add( new Pair<>(pk2.publicImage(), 50L));

        // Note: total 'from' value is 100, total 'to' value is 10
        PublicKey25519Proposition changeAddress = pk5.publicImage();

        // Test 1: exclude pk1 key -> now suitable keys in wallet
        boolean exceptionOccurred = false;
        long fee = 10l;

        ArrayList<byte[]> boxIdsToExclude = new ArrayList<>();
        boxIdsToExclude.add(boxToExclude.id());
        try {
            RegularTransactionCreator.create(wallet, to, new ArrayList<>(), changeAddress, fee, boxIdsToExclude);
        }
        catch (IllegalArgumentException e) {
            exceptionOccurred = true;
        }
        assertEquals("Exception expected: key in wallet must be ignored", true, exceptionOccurred);
    }

    @Test
    public void RegularTransactionCreator_InputsOrderTest() {
        List<Pair<Box, Long>> boxesWithCreationTime = new ArrayList<>();
        RegularBox expectedBox = new RegularBox(pk3.publicImage(), 1, 10);
        boxesWithCreationTime.add(new Pair<>(new RegularBox(pk1.publicImage(), 1, 10), 3L));
        boxesWithCreationTime.add(new Pair<>(expectedBox, 1L));
        boxesWithCreationTime.add(new Pair<>(new RegularBox(pk2.publicImage(), 1, 10), 2L));

        List<Secret> secrets = new ArrayList<>();
        secrets.add(pk1);
        secrets.add(pk2);
        secrets.add(pk3);

        NodeWallet wallet = new TransactionCreatorNodeWallet(boxesWithCreationTime, secrets);

        List<Pair<PublicKey25519Proposition, Long>> to = new ArrayList<>();
        to.add( new Pair<>(pk4.publicImage(), 10L));
        long fee = 0L;

        RegularTransaction transaction = RegularTransactionCreator.create(wallet, to, new ArrayList<>(), pk5.publicImage(), fee, new ArrayList<byte[]>());
        assertEquals("Only one box unlocker expected.", 1, transaction.unlockers().size());
        assertArrayEquals("Another box expected.", expectedBox.id(), transaction.unlockers().get(0).closedBoxId());
    }

    @Test
    public void RegularTransactionCreator_NullArgumentTest() {
        List<Pair<PublicKey25519Proposition, Long>> to = new ArrayList<>();
        to.add( new Pair<>(pk4.publicImage(), 20L));
        to.add( new Pair<>(pk5.publicImage(), 30L));


        // Test 1
        boolean exceptionOccurred = false;
        try {
            RegularTransaction transaction = RegularTransactionCreator.create(null, to, new ArrayList<>(), pk6.publicImage(), 10, new ArrayList<byte[]>());
        }
        catch (IllegalArgumentException e) {
            exceptionOccurred = true;
        }
        assertEquals("Test1: Exception expected: wallet is null", true, exceptionOccurred);


        // Test 2
        exceptionOccurred = false;
        try {
            RegularTransaction transaction = RegularTransactionCreator.create(defaultWallet, null, new ArrayList<>(), pk6.publicImage(), 10, new ArrayList<byte[]>());
        }
        catch (IllegalArgumentException e) {
            exceptionOccurred = true;
        }
        assertEquals("Test2: Exception expected: 'to' is null", true, exceptionOccurred);


        // Test 3
        exceptionOccurred = false;
        try {
            RegularTransaction transaction = RegularTransactionCreator.create(defaultWallet, to, new ArrayList<>(), null, 10, new ArrayList<byte[]>());
        }
        catch (IllegalArgumentException e) {
            exceptionOccurred = true;
        }
        assertEquals("Test3: Exception expected: change address is null", true, exceptionOccurred);


        // Test 4
        exceptionOccurred = false;
        try {
            RegularTransaction transaction = RegularTransactionCreator.create(defaultWallet, to, new ArrayList<>(), pk6.publicImage(), 10, null);
        }
        catch (IllegalArgumentException e) {
            exceptionOccurred = true;
        }
        assertEquals("Test4: Exception expected: boxIdsToExclude is null", true, exceptionOccurred);
    }
}