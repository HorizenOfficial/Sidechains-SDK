import scala.Tuple2;
import scala.collection.Seq;
import scala.collection.Traversable;
import scorex.core.transaction.box.Box;

abstract class SecretC<S extends Secret, PK extends Proposition,
        PR extends Proof<PK>, G extends SecretC >
        implements SecretCompanionWrapper<S, PK, PR>
{
    @Override
    public boolean verify(byte[] message, Object publicImage, Object proof) {
        return verify1(message, (PK)publicImage, (PR)proof);
    }
    G getInstance() {
        return null;
    }
    @Override
    public abstract boolean verify1(byte[] message, PK publicImage, PR proof);
    //@Override
    //boolean verify(byte[] message, PK publicImage, PR proof);
}


abstract class AbstractNoncedBoxTransactionTest<T extends AbstractNoncedBoxTransactionTest, P extends Proposition, B extends Box<P>> extends BoxTransaction<P,B>{
    abstract T getInstance(P prop, Nonce n,  int value);

    class PKDestination {
        P p; int Value;
    }
    abstract Iterable<PKDestination> getTo();


    Traversable<PublicKey25519NoncedBox> newBoxes() {

        for( PKDestination d: getTo()) {
            Nonce nonce = nonceFromDigest(Blake2b256(prop.pubKeyBytes ++ hashNoNonces ++ Ints.toByteArray(idx)));
            getInstance(d.p, nonce, d.Value);
        }


    }
}

class RegTransaction extends AbstractNoncedBoxTransactionTest<RegTransaction, Proposition, Box<Proposition>> {
    @Override
    RegTransaction getInstance(Proposition prop, Nonce n, int value) {
        return null;
    }
}








interface TransactionDestinationOutput {

}

class BoxTransactionDestinationOutput<P extends Proposition> implements TransactionDestinationOutput
{
    private P _proposition;
    private long _value;

    BoxTransactionDestinationOutput(P proposition, long value) {
        _proposition = proposition;
        _value = value;
    }

    public P propostion() { return _proposition; }

    public long value() { return _value; }
}

abstract class AbstractNoncedBoxTransaction<T extends AbstractNoncedBoxTransaction, D extends BoxTransactionDestinationOutput,
        P extends Proposition, B extends NoncedBox<P>> extends BoxTransaction<P, B> {

    private byte[] hashWithoutNonces(){
        // return concatenation of newBoxes()[i].proposition().bytes() + unlockers()[i].closedBoxId() + timestamp + fee
        return new byte[0];
    }

    abstract protected ArrayList<D> getDestinationOutputs();
    abstract protected long calculateNonce(D to, int idx, byte[] txHashWithoutNonce);
    abstract T getInstance(D to, long nonce);

    @Override
    public ArrayList<B> newBoxes() {
        ArrayList<B> boxes = new ArrayList<B>();
        ArrayList<D> to = getDestinationOutputs();
        for (int i = 0; i < to.size(); i++) {
            long nonce = calculateNonce(to.get(i), i, hashWithoutNonces());
            boxes.add(getInstance(to.get(i), nonce));
        }
        return null;
    }
}


abstract class NoncedBoxTransactionTest<P extends Proposition, B extends NoncedBox<P> > extends BoxTransaction<P, B>
{
    public abstract NoncedBoxTransactionCompanionTest companion();
}

interface NoncedBoxTransactionCompanionTest<T extends NoncedBoxTransaction, P extends Proposition, B extends NoncedBox<P>>
{
    byte[] hashWithoutNonces(T transaction);

    ArrayList<B> calculateNonces(ArrayList<B> to, byte[] hashWithoutNonces);
}


















interface Test<S extends scorex.core.transaction.state.Secret, P extends scorex.core.transaction.box.proposition.ProofOfKnowledgeProposition<S> > extends scorex.core.transaction.state.SecretCompanion<S>
{
    @Override
    boolean owns(S secret, Box<?> box);

    @Override
    Object sign(S secret, byte[] message);

    @Override
    boolean verify(byte[] message, Object publicImage, Object proof);

    @Override
    Tuple2<S, Object> generateKeys(byte[] randomSeed);
}












