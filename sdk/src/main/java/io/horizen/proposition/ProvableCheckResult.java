package io.horizen.proposition;

import com.horizen.secret.Secret;

import java.util.List;

/**
 * Data structure used to represent the result of a ProofOfKnowledgeProposition check against a list of secrets
 */
public interface ProvableCheckResult<S extends Secret> {

    /**
     * @return true if the proposition can be proved by one or more secrets provided in the check
     */
    boolean canBeProved();

    /**
     * @return the list of all the secrets that can be used to build the proof.
     * Please note that, depending on the type of proposition, they may not all be needed in conjunction to build
     * a valid proof.
     * For example we may have a proposition that can be unlocked by just one between a list of three compatible secrets.
     */
    List<S> secretsNeeded();
}
