package com.horizen.node;

import com.horizen.box.Box;
import com.horizen.proposition.*;
import com.horizen.secret.PrivateKey25519;
import com.horizen.secret.SchnorrSecret;
import com.horizen.secret.Secret;
import com.horizen.secret.VrfSecretKey;

import java.util.*;

public interface NodeWallet {

    // boxes are sorted by creation time in wallet from oldest to newest
    List<Box<Proposition>> allBoxes();

    // boxes are sorted by creation time in wallet from oldest to newest
    List<Box<Proposition>> allBoxes(List<byte[]> boxIdsToExclude);

    List<Box<Proposition>> boxesOfType(Class<? extends Box<? extends Proposition>> type);

    List<Box<Proposition>> boxesOfType(Class<? extends Box<? extends Proposition>> type, List<byte[]> boxIdsToExclude);

    Long boxesBalance(Class<? extends Box<? extends Proposition>> type);

    Long allCoinsBoxesBalance();

    Optional<PrivateKey25519> secretByPublicKey25519Proposition(PublicKey25519Proposition proposition);

    Optional<SchnorrSecret> secretBySchnorrProposition(SchnorrProposition proposition);

    Optional<VrfSecretKey> secretByVrfPublicKey(VrfPublicKey proposition);

    <S extends Secret> List<S> secretsByProposition(ProofOfKnowledgeProposition<S> proposition);

    List<Secret> allSecrets();

    List<Secret> secretsOfType(Class<? extends Secret> type);

    byte[] walletSeed();
}
