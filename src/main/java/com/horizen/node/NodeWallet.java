package com.horizen.node;

import com.horizen.box.Box;
import com.horizen.proposition.ProofOfKnowledgeProposition;
import com.horizen.secret.Secret;
import javafx.util.Pair;

import java.util.List;

public interface NodeWallet {

    List<Pair<Box, Long>> boxesWithCreationTime();

    Secret secretByPublicImage(ProofOfKnowledgeProposition publicImage);
}
