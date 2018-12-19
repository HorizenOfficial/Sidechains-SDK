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

final class ProofOfCoinBurn implements Proof<ProofOfCoinBurnProposition>
{
    @Override
    public boolean isValid(ProofOfCoinBurnProposition proposition, byte[] merkleRoot) {
        // Get hash of proposition._mainchainCoinBurnTransfer
        // calculate the merkle root for hash and proposition._merklePath
        // compare calculated root with provided
        return false;
    }

    @Override
    public byte[] bytes() {
        return new byte[0];
    }

    @Override
    public ProofSerializer serializer() {
        return null;
    }
}


final class ProofOfBeingIncludedIntoCertificate implements Proof<ProofOfBeingIncludedIntoCertificateProposition>
{
    @Override
    public boolean isValid(ProofOfBeingIncludedIntoCertificateProposition proposition, byte[] merkleRoot) {
        // Get hash of proposition._mainchainCertifierLockTransfer
        // calculate the merkle root for hash and proposition._merklePath
        // compare calculated root with provided
        return false;
    }

    @Override
    public byte[] bytes() {
        return new byte[0];
    }

    @Override
    public ProofSerializer serializer() {
        return null;
    }
}