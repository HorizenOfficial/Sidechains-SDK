package com.horizen.proposition;

/*
trait Proposition extends BytesSerializable

trait ProofOfKnowledgeProposition[S <: Secret] extends Proposition
 */

public interface Proposition extends scorex.core.transaction.box.proposition.Proposition
{
    /** This function repeats bytes() function from BytesSerializable scala trait because
     * java removed it after the import.
     * @return byte representation of object
     */
    @Override
    default byte[] bytes() {
        return serializer().toBytes(this);
    }

    @Override
    PropositionSerializer serializer();
}
