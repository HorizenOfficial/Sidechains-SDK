package com.horizen.node;

import com.horizen.box.Box;
import com.horizen.proposition.Proposition;
import com.horizen.proposition.ProofOfKnowledgeProposition;
import com.horizen.secret.Secret;

import java.util.*;

public interface NodeWallet {

    // boxes are sorted by creation time in wallet from oldest to newest
    List<Box<Proposition>> allBoxes();

    // boxes are sorted by creation time in wallet from oldest to newest
    List<Box<Proposition>> allBoxes(List<byte[]> boxIdsToExclude);

    List<Box<Proposition>> boxesOfType(Class<? extends Box<? extends Proposition>> type);

    List<Box<Proposition>> boxesOfType(Class<? extends Box<? extends Proposition>> type, List<byte[]> boxIdsToExclude);

    Long boxesBalance(Class<? extends Box<? extends Proposition>> type);

    Long allBoxesBalance();

    Optional<Secret> secretByPublicKey(Proposition publicKey);

    List<Secret> allSecrets();

    List<Secret> secretsOfType(Class<? extends Secret> type);

    String generateNewAddress();
}
