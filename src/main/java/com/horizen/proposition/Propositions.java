import scala.util.Try;
import scorex.core.serialization.Serializer;
import scorex.core.settings.ScorexSettings;

import java.security.PublicKey;

/*
trait Proposition extends BytesSerializable

trait ProofOfKnowledgeProposition[S <: Secret] extends Proposition
 */

interface Proposition extends scorex.core.transaction.box.proposition.Proposition
{
    @Override
    byte[] bytes();

    @Override
    PropositionSerializer serializer();
}

interface PropositionSerializer<P extends Proposition> extends Serializer<P>
{
    @Override
    byte[] toBytes(P obj);

    @Override
    Try<P> parseBytes(byte[] bytes);
}


interface ProofOfKnowledgeProposition<S extends Secret> extends Proposition
{

}

// TO DO: check usage of ScorexEncodingImpl and Scorex core Encoders
class PublicKey25519Proposition<PK extends PrivateKey25519> extends ScorexEncodingImpl implements ProofOfKnowledgeProposition<PK>
{
    // to do: change to scorex.crypto.PublicKey
    PublicKey _pubKeyBytes;

    public PublicKey25519Proposition(PublicKey pubKeyBytes)
    {
        // require check
        _pubKeyBytes = pubKeyBytes;
    }

    public PublicKey pubKeyBytes() {
        return _pubKeyBytes;
    }

    @Override
    public byte[] bytes() {
        return new byte[0];
    }

    @Override
    public PublicKey25519PropositionSerializer serializer() {
        return new PublicKey25519PropositionSerializer();
    }

    @Override
    public int hashCode() {
        return 0;
    }

    @Override
    public boolean equals(Object obj) {
        return false;
    }
}

class PublicKey25519PropositionSerializer<PKP extends PublicKey25519Proposition> implements PropositionSerializer<PKP>
{

    @Override
    public byte[] toBytes(PKP obj) {
        return new byte[0];
    }

    @Override
    public Try<PKP> parseBytes(byte[] bytes) {
        return null;
    }
}


final class ProofOfCoinBurnProposition extends ScorexEncodingImpl implements Proposition
{
    MainchainTransaction _mainchainCoinBurnTransfer;
    MainchainTrMerklePath _merklePath;

    public ProofOfCoinBurnProposition(MainchainTransaction mainchainCoinBurnTransfer,
                                      MainchainTrMerklePath merklePath) {
        _mainchainCoinBurnTransfer = mainchainCoinBurnTransfer;
        _merklePath = merklePath;
    }


    @Override
    public byte[] bytes() {
        return new byte[0];
    }

    @Override
    public PropositionSerializer serializer() {
        return null;
    }
}

final class ProofOfBeingIncludedIntoCertificateProposition extends ScorexEncodingImpl implements Proposition
{
    MainchainTransaction _mainchainCertifierLockTransfer;
    MainchainTrMerklePath _merklePath;

    public ProofOfBeingIncludedIntoCertificateProposition(MainchainTransaction mainchainCertifierLockTransfer,
                                      MainchainTrMerklePath merklePath) {
        _mainchainCertifierLockTransfer = mainchainCertifierLockTransfer;
        _merklePath = merklePath;
    }


    @Override
    public byte[] bytes() {
        return new byte[0];
    }

    @Override
    public PropositionSerializer serializer() {
        return null;
    }
}