package com.horizen.node;

import com.horizen.box.Box;
import com.horizen.proposition.ProofOfKnowledgeProposition;
import com.horizen.secret.Secret;

import java.util.*;

public interface NodeWallet {

    // boxes are sorted by creation time in wallet from oldest to newest
    List<Box> allBoxes();

    // boxes are sorted by creation time in wallet from oldest to newest
    List<Box> allBoxes(List<byte[]> boxIdsToExclude);

    List<Box> boxesOfType(Class<? extends Box> type);

    List<Box> boxesOfType(Class<? extends Box> type, List<byte[]> boxIdsToExclude);

    Long boxesBalance(Class<? extends Box> type);

    Optional<Secret> secretByPublicKey(ProofOfKnowledgeProposition publicKey);

    List<Secret> allSecrets();

    List<Secret> secretsOfType(Class<? extends Secret> type);
}
