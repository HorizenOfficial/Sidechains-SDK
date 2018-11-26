import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import scala.util.Try;
import scorex.core.serialization.BytesSerializable;
import scorex.core.serialization.Serializer;
import scorex.core.utils.ScorexEncoder;


/**
 *
 * IMPORTANT: Scorex Core "core.scala' file different from TreasuryCrypto 'core.scala'
 * TreasuryCrypto has older version of Scorex Core (before Catena changes on July 2018)
 *
 */

abstract class Transaction extends scorex.core.transaction.Transaction
{
    @Override
    public final scorex.core.ModifierTypeId modifierTypeId() {
        return (scorex.core.ModifierTypeId)super.modifierTypeId();
    }

    @Override
    public final scorex.core.ModifierId id() {
        return (scorex.core.ModifierId)super.id();
    }

    @Override
    public final byte[] bytes() {
        return serializer().toBytes(this);
    }

    @Override
    public abstract byte[] messageToSign();

    @Override
    public abstract TransactionSerializer serializer();

    public abstract scorex.core.ModifierTypeId transactionTypeId();
}


interface TransactionSerializer<T extends Transaction> extends Serializer<T>
{
    @Override
    byte[] toBytes(T obj);

    @Override
    Try<T> parseBytes(byte[] bytes);
}

// TO DO: do we need to put fee and timestamps members inside this abstract class?
abstract class BoxTransaction<P extends Proposition, B extends Box<P> > extends Transaction
{
    // TO DO: think about proper collection type
    public abstract ArrayList<BoxUnlocker<P>> unlockers();

    public abstract ArrayList<B> newBoxes();

    public abstract long fee();

    public abstract long timestamp();

    @Override
    public byte[] messageToSign() {
        // TO DO: return concatenation of newBoxes()[i].bytes() + unlockers()[i].closedBoxId() + timestamp + fee
        return new byte[0];
    }

    public TransactionIncompatibilityChecker incompatibilityChecker() {
        return new DefaultTransactionIncompatibilityChecker();
    }
}


interface TransactionIncompatibilityChecker<T extends BoxTransaction>
{
    boolean hasIncompatibleTransactions(T newTx, ArrayList<BoxTransaction> currentTxs);
}

class DefaultTransactionIncompatibilityChecker implements TransactionIncompatibilityChecker<BoxTransaction>
{
    @Override
    public boolean hasIncompatibleTransactions(BoxTransaction newTx, ArrayList<BoxTransaction> currentTxs) {
        // check intersections between spending boxes of current and txs
        return false;
    }
}

abstract class NoncedBoxTransaction<P extends Proposition, B extends NoncedBox<P> > extends BoxTransaction<P, B>
{

}


final class RegularTransaction extends NoncedBoxTransaction<PublicKey25519Proposition, RegularBox>
{

    @Override
    public RegularTransactionSerializer serializer() {
        return new RegularTransactionSerializer();
    }

    @Override
    public ArrayList<BoxUnlocker<PublicKey25519Proposition>> unlockers() {
        return null;
    }

    @Override
    public ArrayList<RegularBox> newBoxes() {
        return null;
    }

    @Override
    public long fee() {
        return 0;
    }

    @Override
    public long timestamp() {
        return 0;
    }

    @Override
    public scorex.core.ModifierTypeId transactionTypeId() {
        return null;// scorex.core.ModifierTypeId @@ 1.toByte();
    }
}

class RegularTransactionSerializer implements TransactionSerializer<RegularTransaction>
{
    private ListSerializer<RegularBox> _boxSerializer;
    // todo: keep another serializers for inputs and signatures(secrets)

    RegularTransactionSerializer() {
        HashMap<Integer, Serializer<RegularBox>> supportedBoxSerializers = new HashMap<Integer, Serializer<RegularBox>>();
        supportedBoxSerializers.put(1, new RegularBoxSerializer());

        _boxSerializer  = new ListSerializer<RegularBox>(supportedBoxSerializers);
    }

    @Override
    public byte[] toBytes(RegularTransaction obj) {
        return _boxSerializer.toBytes(obj.newBoxes());
    }

    @Override
    public Try<RegularTransaction> parseBytes(byte[] bytes) {
        ArrayList<RegularBox> boxes = _boxSerializer.parseBytes(bytes).get();

        // create RegualrTransaction and init with Boxes
        return null;
    }
}


final class ForwardTransaction extends NoncedBoxTransaction<PublicKey25519Proposition, RegularBox>
{

    @Override
    public ForwardTransactionSerializer serializer() {
        return new ForwardTransactionSerializer();
    }

    // nothing to spent
    @Override
    public ArrayList<BoxUnlocker<PublicKey25519Proposition>> unlockers() {
        return new ArrayList<BoxUnlocker<PublicKey25519Proposition>>();
    }

    @Override
    public ArrayList<RegularBox> newBoxes() {
        return null;
    }

    @Override
    public long fee() {
        return 0;
    }

    @Override
    public long timestamp() {
        return 0;
    }

    @Override
    public scorex.core.ModifierTypeId transactionTypeId() {
        return null; // scorex.core.ModifierTypeId @@ 2.toByte
    }
}

class ForwardTransactionSerializer implements TransactionSerializer<ForwardTransaction>
{
    private ListSerializer<RegularBox> _boxSerializer;

    ForwardTransactionSerializer() {
        HashMap<Integer, Serializer<RegularBox>> supportedBoxSerializers = new HashMap<Integer, Serializer<RegularBox>>();
        supportedBoxSerializers.put(1, new RegularBoxSerializer());

        _boxSerializer  = new ListSerializer<RegularBox>(supportedBoxSerializers);
    }

    @Override
    public byte[] toBytes(ForwardTransaction obj) {
        return _boxSerializer.toBytes(obj.newBoxes());
    }

    @Override
    public Try<ForwardTransaction> parseBytes(byte[] bytes) {
        ArrayList<RegularBox> boxes = _boxSerializer.parseBytes(bytes).get();

        // create RegualrTransaction and init with Boxes
        return null;
    }
}


final class BackwardTransaction extends NoncedBoxTransaction<PublicKey25519Proposition, RegularBox>
{
    @Override
    public BackwardTransactionSerializer serializer() {
        return new BackwardTransactionSerializer();
    }

    @Override
    public ArrayList<BoxUnlocker<PublicKey25519Proposition>> unlockers() { return null; }

    // nothing to create
    @Override
    public ArrayList<RegularBox> newBoxes() {
        return new ArrayList<RegularBox>();
    }

    @Override
    public long fee() {
        return 0;
    }

    @Override
    public long timestamp() {
        return 0;
    }

    @Override
    public scorex.core.ModifierTypeId transactionTypeId() {
        return null; // scorex.core.ModifierTypeId @@ 3.toByte
    }
}

class BackwardTransactionSerializer implements TransactionSerializer<BackwardTransaction>
{
    private ListSerializer<RegularBox> _boxSerializer;

    BackwardTransactionSerializer() {
        HashMap<Integer, Serializer<RegularBox>> supportedBoxSerializers = new HashMap<Integer, Serializer<RegularBox>>();
        supportedBoxSerializers.put(1, new RegularBoxSerializer());

        _boxSerializer  = new ListSerializer<RegularBox>(supportedBoxSerializers);
    }

    @Override
    public byte[] toBytes(BackwardTransaction obj) {
        return _boxSerializer.toBytes(obj.newBoxes());
    }

    @Override
    public Try<BackwardTransaction> parseBytes(byte[] bytes) {
        ArrayList<RegularBox> boxes = _boxSerializer.parseBytes(bytes).get();

        // create RegualrTransaction and init with Boxes
        return null;
    }
}


class ListSerializer<T extends BytesSerializable> implements Serializer<ArrayList<T>> {
    private HashMap<Integer, Serializer<T>> _serializers; // unique key + serializer

    ListSerializer(HashMap<Integer, Serializer<T>> serializers) {
        _serializers = serializers;
    }

    @Override
    public byte[] toBytes(ArrayList<T> obj) {
        ArrayList<Integer> lengthList = new ArrayList<Integer>();

        ByteArrayOutputStream res = new ByteArrayOutputStream();
        ByteArrayOutputStream entireRes = new ByteArrayOutputStream();
        for (T t : obj) {
            Integer idOfSerializer = 0;// get id from _serializers
            byte[] tBytes = t.bytes();
            lengthList.add(idOfSerializer.byteValue() + tBytes.length);

            try {
                entireRes.write(idOfSerializer);
                entireRes.write(t.bytes());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        try {
            res.write(lengthList.size());
            for (Integer i : lengthList) {
                res.write(i);
            }
            res.write(entireRes.toByteArray());
        } catch (IOException e) {
            e.printStackTrace();
        }

        return res.toByteArray();
    }

    @Override
    public Try<ArrayList<T>> parseBytes(byte[] bytes) {
        // TO DO: implement backward logic
        return null;
    }
}