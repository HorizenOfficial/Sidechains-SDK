import scala.util.Try;
import scorex.core.serialization.Serializer;


/**
 * Just to not farget what is a Box interface
 *
 trait Box[P <: Proposition] extends BytesSerializable {
 val value: Box.Amount
 val proposition: P

 val id: ADKey
 }

 object Box {
 type Amount = Long
 }

 */


interface Box<P extends Proposition> extends scorex.core.transaction.box.Box<P>
{
    @Override
    long value();

    @Override
    P proposition();

    // TO DO: change id to ADKey
    @Override
    Object id();

    @Override
    byte[] bytes();

    @Override
    BoxSerializer serializer();

    scorex.core.ModifierTypeId boxTypeId();
}


interface NoncedBox<P extends Proposition> extends Box<P>
{
    long nonce();
}


interface BoxSerializer<B extends Box> extends Serializer<B>
{
    @Override
    byte[] toBytes(B obj);

    @Override
    Try<B> parseBytes(byte[] bytes);
}


interface CoinsBox<P extends Proposition> extends Box<P>
{

}


abstract class PublicKey25519NoncedBox<PKP extends PublicKey25519Proposition> implements NoncedBox<PKP>
{
    PKP _proposition;
    long _nonce;
    long _value;

    PublicKey25519NoncedBox(PKP proposition,
                            long nonce,
                            long value)
    {
        this._proposition = proposition;
        this._nonce = nonce;
        this._value = value;
    }

    @Override
    public long value() {
        return _value;
    }

    @Override
    public PKP proposition() { return _proposition; }

    @Override
    public long nonce() { return _nonce; }

    @Override
    public Object id() { // actually return ADKey
        return null;
    }

    @Override
    public byte[] bytes() {
        return serializer().toBytes(this);
    }

    @Override
    public PublicKey25519NoncedBoxSerializer serializer() {
        return new PublicKey25519NoncedBoxSerializer();
    }
}

class PublicKey25519NoncedBoxSerializer<PKNB extends PublicKey25519NoncedBox> implements BoxSerializer<PKNB>
{
    @Override
    public Try<PKNB> parseBytes(byte[] bytes) {
        return null;
    }

    @Override
    public byte[] toBytes(PKNB obj) {
        return null;
    }
}

/*class TestBox<PKP extends PublicKey25519Proposition> extends PublicKey25519NoncedBox<PKP>
{

    TestBox(PKP proposition, int nonce, int value) {
        super(proposition, nonce, value);
    }

    public int test() {
        TestSerializer ts = this.serializer();
        return 3;
    }

    @Override
    public TestSerializer serializer() {
        return new TestSerializer();
    }
}

class TestSerializer<TB extends TestBox> extends PublicKey25519NoncedBoxSerializer<TB>
{
    @Override
    public Try<TB> parseBytes(byte[] bytes) {
        return super.parseBytes(bytes);
    }

    @Override
    public byte[] toBytes(TB obj) {
        obj.test();
        return super.toBytes(obj);
    }
}*/


// Example of SDK known Transaction type
final class RegularBox extends PublicKey25519NoncedBox<PublicKey25519Proposition> implements CoinsBox<PublicKey25519Proposition>
{
    RegularBox(PublicKey25519Proposition proposition,
                    int nonce,
                    int value)
    {
        super(proposition, nonce, value);
    }

    @Override
    public RegularBoxSerializer serializer() {
        return new RegularBoxSerializer();
    }

    @Override
    public scorex.core.ModifierTypeId boxTypeId() {
        return null; // scorex.core.ModifierTypeId @@ 3.toByte
    }
}


final class RegularBoxSerializer extends PublicKey25519NoncedBoxSerializer<RegularBox>
{
    @Override
    public Try<RegularBox> parseBytes(byte[] bytes) {
        return super.parseBytes(bytes);
    }

    @Override
    public byte[] toBytes(RegularBox obj) {
        return super.toBytes(obj);
    }
}





class BallotBox extends PublicKey25519NoncedBox<PublicKey25519Proposition>
{
    BallotBox(PublicKey25519Proposition proposition,
                    int nonce)
    {
        super(proposition, nonce, 0);
    }

    @Override
    public scorex.core.ModifierTypeId boxTypeId() {
        return null; // // scorex.core.ModifierTypeId @@ 1.toByte
    }
}