package io.horizen.proof;

import com.fasterxml.jackson.annotation.JsonView;
import io.horizen.proposition.Proposition;
import io.horizen.json.Views;

/*
trait Proof[P <: Proposition] extends BytesSerializable {
  def isValid(proposition: P, message: Array[Byte]): Boolean
}

trait ProofOfKnowledge[S <: Secret, P <: ProofOfKnowledgeProposition[S]] extends Proof[P]
 */

@JsonView(Views.Default.class)
public interface Proof<P extends Proposition>
    extends sparkz.core.transaction.proof.Proof<P>
{
    boolean isValid(P proposition, byte[] message);

    @Override
    default byte[] bytes() {
        return serializer().toBytes(this);
    }

    @Override
    ProofSerializer serializer();
}

