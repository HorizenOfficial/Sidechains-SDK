import scala.util.Try;
import scorex.core.serialization.Serializer;

/*
trait Proof[P <: Proposition] extends BytesSerializable {
  def isValid(proposition: P, message: Array[Byte]): Boolean
}

trait ProofOfKnowledge[S <: Secret, P <: ProofOfKnowledgeProposition[S]] extends Proof[P]
 */

interface Proof<P extends Proposition> extends scorex.core.transaction.proof.Proof<P>
{
    @Override
    boolean isValid(P proposition, byte[] message);

    @Override
    byte[] bytes();

    @Override
    ProofSerializer serializer();
}

interface ProofOfKnowledge<S extends Secret, P extends ProofOfKnowledgeProposition<S> > extends Proof<P>
{

}


interface ProofSerializer<P extends Proof> extends Serializer<P>
{
    @Override
    byte[] toBytes(P obj);

    @Override
    Try<P> parseBytes(byte[] bytes);
}