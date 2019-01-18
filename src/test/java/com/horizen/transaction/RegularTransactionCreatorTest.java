package com.horizen.transaction;

import com.horizen.Wallet;
import com.horizen.box.Box;
import com.horizen.box.RegularBox;
import com.horizen.node.NodeWallet;
import com.horizen.proposition.ProofOfKnowledgeProposition;
import com.horizen.proposition.PublicKey25519Proposition;
import com.horizen.secret.PrivateKey25519;
import com.horizen.secret.Secret;
import javafx.scene.Node;
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

    @Before
    public void beforeEachTest() {
        pair1 = PrivateKey25519.generateKeys("test_seed1".getBytes());
        pair2 = PrivateKey25519.generateKeys("test_seed2".getBytes());
        pair3 = PrivateKey25519.generateKeys("test_seed3".getBytes());
        pair4 = PrivateKey25519.generateKeys("test_seed4".getBytes());
        pair5 = PrivateKey25519.generateKeys("test_seed5".getBytes());
        pair6 = PrivateKey25519.generateKeys("test_seed6".getBytes());
    }

    @Test
    public void RegularTransactionCreator_SuccessCreationTest() {

        List<Pair<Box, Long>> boxesWithCreationTime = new ArrayList<>();
        boxesWithCreationTime.add(new Pair<>(new RegularBox(pair1._2, 1, 30), 1000L));
        boxesWithCreationTime.add(new Pair<>(new RegularBox(pair2._2, 1, 40), 2000L));
        boxesWithCreationTime.add(new Pair<>(new RegularBox(pair3._2, 1, 50), 3000L));

        List<Secret> secrets = new ArrayList<>();
        secrets.add(pair1._1);
        secrets.add(pair2._1);
        secrets.add(pair3._1);

        NodeWallet wallet = new RegularTransactionCreatorNodeWallet(boxesWithCreationTime, secrets);

        List<Pair<PublicKey25519Proposition, Long>> to = new ArrayList<>();
        to.add( new Pair<>(pair4._2, 20L));
        to.add( new Pair<>(pair5._2, 30L));
        RegularTransaction transaction = RegularTransactionCreator.create(wallet, to, pair6._2, 10, new ArrayList<byte[]>());

        assertEquals("RegularTransactionCreator: change expected.", 3, transaction.newBoxes().size());
        assertEquals("RegularTransactionCreator: 2 inputs expected.", 2, transaction.unlockers().size());
    }

    @Test
    public void RegularTransactionCreator_FailureCreationTest() {

    }
}