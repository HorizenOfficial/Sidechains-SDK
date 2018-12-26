package com.horizen.secret;

import com.horizen.proposition.ProofOfKnowledgeProposition;
import com.horizen.proof.ProofOfKnowledge;

import scala.Tuple2;

// TO DO: remove
import scorex.core.transaction.box.Box;


public interface Secret extends scorex.core.transaction.state.Secret
{
    @Override
    SecretCompanion companion();

    @Override
    Secret instance();

    @Override
    ProofOfKnowledgeProposition<Secret> publicImage();

    @Override
    byte[] bytes();

    @Override
    SecretSerializer serializer();

    // TO DO: uncomment and fix compiler error with acces to scla package object in java interface
    byte secretTypeId();
}


/**
 * TO DO: scorex.core.transaction.state.SecretCompanion must provide PK, PR and Box as a polymorphic objects of the class
 * to be Java friendly and to allow us to override this class methods for nested Objects.
 */

interface SecretCompanion<S extends Secret> extends scorex.core.transaction.state.SecretCompanion<S>
{
    // Note: here we use scorex.core.transaction.box.Box trait, but not our Java Box interface
    @Override
    boolean owns(S secret, Box<?> box);

    // TO DO: check ProofOfKnowledge usage
    @Override
    ProofOfKnowledge sign(S secret, byte[] message);

    // TO DO: change Objects to proper types
    @Override
    boolean verify(byte[] message, Object publicImage, scorex.core.transaction.box.proposition.ProofOfKnowledgeProposition<scorex.core.transaction.state.Secret> proof);

    // TO DO: change Objects to proper types
    @Override
    Tuple2<S, Object> generateKeys(byte[] randomSeed);
}

