package io.horizen.node;

import io.horizen.proposition.*;
import io.horizen.secret.PrivateKey25519;
import io.horizen.secret.SchnorrSecret;
import io.horizen.secret.Secret;
import io.horizen.secret.VrfSecretKey;

import java.util.List;
import java.util.Optional;

public interface NodeWalletBase {
    Optional<Secret> secretByPublicKey(Proposition publicKey);

    List<Secret> allSecrets();

    List<Secret> secretsOfType(Class<? extends Secret> type);

    byte[] walletSeed();

    Optional<PrivateKey25519> secretByPublicKey25519Proposition(PublicKey25519Proposition proposition);

    Optional<SchnorrSecret> secretBySchnorrProposition(SchnorrProposition proposition);


    Optional<VrfSecretKey> secretByVrfPublicKey(VrfPublicKey proposition);


    <S extends Secret> List<S> secretsByProposition(ProofOfKnowledgeProposition<S> proposition);

    <S extends Secret> Optional<S> secretByPublicKeyBytes(byte[] proposition);

}
