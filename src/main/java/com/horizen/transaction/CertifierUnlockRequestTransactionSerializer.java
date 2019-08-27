package com.horizen.transaction;

import com.horizen.box.NoncedBox;
import com.horizen.proposition.Proposition;
import com.horizen.utils.ListSerializer;
import com.horizen.utils.DynamicTypedSerializer;
import scala.util.Try;
import scorex.core.serialization.ScorexSerializer;
import scorex.util.serialization.Reader;
import scorex.util.serialization.Writer;

import java.util.HashMap;
import java.util.List;

public class CertifierUnlockRequestTransactionSerializer<T extends CertifierUnlockRequestTransaction>
    implements TransactionSerializer<T>
{
    private ListSerializer<NoncedBox<Proposition>> _boxSerializer;

    CertifierUnlockRequestTransactionSerializer() {
        DynamicTypedSerializer<NoncedBox<Proposition>, ScorexSerializer<? extends NoncedBox<Proposition>>> supportedBoxCompanion =
                new DynamicTypedSerializer<>(
                        new HashMap<Byte, ScorexSerializer<? extends NoncedBox<Proposition>>>() {{
                            // put(RegularBox.BOX_TYPE_ID, RegularBoxSerializer.getSerializer())
                            // TO DO: update supported serializers list
                        }}, new HashMap<>());


        _boxSerializer  = new ListSerializer<NoncedBox<Proposition>>(supportedBoxCompanion);
    }

    /*
    @Override
    public byte[] toBytes(T obj) {
        return _boxSerializer.toBytes(obj.newBoxes());
    }

    @Override
    public Try<T> parseBytesTry(byte[] bytes) {
        List<NoncedBox<Proposition>> boxes = _boxSerializer.parseBytesTry(bytes).get();
        return null;
    }
    */

    @Override
    public void serialize(T transaction, Writer writer) {
        writer.putBytes(_boxSerializer.toBytes(transaction.newBoxes()));
    }

    @Override
    public T parse(Reader reader) {
        List<NoncedBox<Proposition>> boxes = _boxSerializer.parse(reader);
        return null;
    }
}
