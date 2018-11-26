import scala.Tuple2;
import scala.util.Try;
import scorex.core.serialization.Serializer;
import scorex.core.transaction.box.Box; // To Do: remove

import java.security.PrivateKey;
import java.security.PublicKey;


interface Secret extends scorex.core.transaction.state.Secret
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

    scorex.core.ModifierTypeId secretTypeId();
}


interface SecretSerializer<S extends Secret> extends Serializer<S>
{
    @Override
    byte[] toBytes(S obj);

    @Override
    Try<S> parseBytes(byte[] bytes);
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
    boolean verify(byte[] message, Object publicImage, Object proof);

    // TO DO: change Objects to proper types
    @Override
    Tuple2<S, Object> generateKeys(byte[] randomSeed);
}


class PrivateKey25519 implements Secret
{
    // TO DO: change to scorex.crypto.{PublicKey,PrivateKey}
    PrivateKey _privateKeyBytes;
    PublicKey _publicKeyBytes;

    public PrivateKey25519(PrivateKey privateKeyBytes, PublicKey publicKeyBytes)
    {
        // TO DO: require check
        _privateKeyBytes = privateKeyBytes;
        _publicKeyBytes = publicKeyBytes;
    }

    @Override
    public PrivateKey25519Companion companion() {
        return new PrivateKey25519Companion();
    }

    @Override
    public PrivateKey25519 instance() {
        return this;
    }

    @Override
    public PublicKey25519Proposition publicImage() {
        return new PublicKey25519Proposition(_publicKeyBytes);
    }

    @Override
    public byte[] bytes() {
        return serializer().toBytes(this);
    }

    @Override
    public PrivateKey25519Serializer serializer() {
        return new PrivateKey25519Serializer();
    }

    @Override
    public scorex.core.ModifierTypeId secretTypeId() {
        return null; // scorex.core.ModifierTypeId @@ 3.toByte
    }
}


class PrivateKey25519Serializer<S extends PrivateKey25519> implements SecretSerializer<S>
{
    @Override
    public byte[] toBytes(S obj) {
        return new byte[0];
    }

    @Override
    public Try<S> parseBytes(byte[] bytes) {
        return null;
    }
}


class PrivateKey25519Companion<S extends PrivateKey25519> implements SecretCompanion<S>
{

    @Override
    public boolean owns(S secret, Box<?> box) {
        return false;
    }

    @Override
    public ProofOfKnowledge sign(S secret, byte[] message) {
        return null;
    }

    @Override
    public boolean verify(byte[] message, Object publicImage, Object proof) {
        return false;
    }

    @Override
    public Tuple2<S, Object> generateKeys(byte[] randomSeed) {
        return null;
    }
}

/*interface SecretJ2 extends SecretJ
{
    @Override
    SecretCompanionJ<? extends SecretJ2> companion();

    @Override
    SecretJ2 instance();

    @Override
    ProofOfKnowledgeProposition<? extends SecretJ2> publicImage();

    @Override
    byte[] bytes();

    @Override
    SecretSerializer serializer();
}*/