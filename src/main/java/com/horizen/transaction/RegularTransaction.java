package com.horizen.transaction;

import com.google.common.primitives.Bytes;
import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;
import com.horizen.box.BoxUnlocker;
import com.horizen.box.RegularBox;
import com.horizen.box.RegularBoxSerializer;
import com.horizen.proof.Proof;
import com.horizen.proposition.PublicKey25519Proposition;
import com.horizen.proposition.PublicKey25519PropositionSerializer;
import com.horizen.proof.Signature25519;
import com.horizen.proof.Signature25519Serializer;
import com.horizen.secret.PrivateKey25519;
import com.horizen.utils.ListSerializer;
import com.horizen.utils.BytesUtils;
import scala.util.Failure;
import scala.util.Success;
import scala.util.Try;
import scorex.crypto.hash.Blake2b256;
import javafx.util.Pair;
import java.io.ByteArrayOutputStream;
import java.util.*;

public final class RegularTransaction extends SidechainTransaction<PublicKey25519Proposition, RegularBox>
{

    public static final byte TRANSACTION_TYPE_ID = 1;

    private List<RegularBox> _inputs;
    private List<Pair<PublicKey25519Proposition, Long>> _outputs;
    private List<Signature25519> _signatures;
    private long _fee;
    private long _timestamp;

    // We don't need to calculate the next values each time, because transaction is immutable.
    private byte[] _hashWithoutNonce;
    private List<RegularBox> _newBoxes;
    private List<BoxUnlocker<PublicKey25519Proposition>> _unlockers;

    // Serializers definition
    private static ListSerializer<RegularBox> _boxSerializer =
            new ListSerializer<>(RegularBoxSerializer.getSerializer(), MAX_TRANSACTION_UNLOCKERS);
    private static ListSerializer<PublicKey25519Proposition> _propositionSerializer =
            new ListSerializer<>(PublicKey25519PropositionSerializer.getSerializer(), MAX_TRANSACTION_NEW_BOXES);
    private static ListSerializer<Signature25519> _signaturesSerializer =
            new ListSerializer<>(Signature25519Serializer.getSerializer(), MAX_TRANSACTION_UNLOCKERS);

    private RegularTransaction(List<RegularBox> inputs,
                               List<Pair<PublicKey25519Proposition, Long>> outputs,
                               List<Signature25519> signatures,
                               long fee,
                               long timestamp) {
        if(inputs.size() != signatures.size())
            throw new IllegalArgumentException("Inputs list size is different to signatures list size!");
        _inputs = inputs;
        _outputs = outputs;
        _signatures = signatures;
        _fee = fee;
        _timestamp = timestamp;
    }

    @Override
    public TransactionSerializer serializer() {
        return RegularTransactionSerializer.getSerializer();
    }

    @Override
    public synchronized List<BoxUnlocker<PublicKey25519Proposition>> unlockers() {
        if(_unlockers == null) {
            _unlockers = new ArrayList<>();
            for (int i = 0; i < _inputs.size() && i < _signatures.size(); i++) {
                int finalI = i;
                _unlockers.add(new BoxUnlocker<PublicKey25519Proposition>() {
                    @Override
                    public byte[] closedBoxId() {
                        return _inputs.get(finalI).id();
                    }

                    @Override
                    public Proof<PublicKey25519Proposition> boxKey() {
                        return _signatures.get(finalI);
                    }
                });
            }
        }
        return Collections.unmodifiableList(_unlockers);
    }

    @Override
    public synchronized List<RegularBox> newBoxes() {
        if(_newBoxes == null) {
            _newBoxes = new ArrayList<>();
            for (int i = 0; i < _outputs.size(); i++) {
                byte[] hash = Blake2b256.hash(Bytes.concat(_outputs.get(i).getKey().pubKeyBytes(), hashWithoutNonce(), Ints.toByteArray(i)));
                long nonce = BytesUtils.getLong(hash, 0);
                _newBoxes.add(new RegularBox(_outputs.get(i).getKey(), nonce, _outputs.get(i).getValue()));
            }
        }
        return Collections.unmodifiableList(_newBoxes);
    }

    @Override
    public long fee() {
        return _fee;
    }

    @Override
    public long timestamp() {
        return _timestamp;
    }

    @Override
    public boolean semanticValidity() {
        if(_fee < 0 || _timestamp < 0)
            return false;

        // check that we have enough proofs and try to open each box only once.
        if(_inputs.size() != _signatures.size() || _inputs.size() != boxIdsToOpen().size())
            return false;
        for(Pair<PublicKey25519Proposition, Long> output : _outputs)
            if(output.getValue() <= 0)
                return false;

        for(int i = 0; i < _inputs.size(); i++) {
            if (!_signatures.get(i).isValid(_inputs.get(i).proposition(), messageToSign()))
                return false;
        }

        return true;
    }

    @Override
    public byte transactionTypeId() {
        return TRANSACTION_TYPE_ID;
    }

    private synchronized byte[] hashWithoutNonce() {
        if(_hashWithoutNonce == null) {
            ByteArrayOutputStream unlockersStream = new ByteArrayOutputStream();
            for (BoxUnlocker<PublicKey25519Proposition> u : unlockers())
                unlockersStream.write(u.closedBoxId(), 0, u.closedBoxId().length);

            ByteArrayOutputStream newBoxesStream = new ByteArrayOutputStream();
            for (Pair<PublicKey25519Proposition, Long> output : _outputs)
                newBoxesStream.write(output.getKey().pubKeyBytes(), 0, output.getKey().pubKeyBytes().length);


            _hashWithoutNonce = Bytes.concat(unlockersStream.toByteArray(),
                    newBoxesStream.toByteArray(),
                    Longs.toByteArray(_timestamp),
                    Longs.toByteArray(_fee));

        }
        return _hashWithoutNonce;
    }

    @Override
    public byte[] bytes() {
        byte[] inputBoxesBytes = _boxSerializer.toBytes(_inputs);

        List<Pair<PublicKey25519Proposition, Long>> outputs = _outputs;
        List<PublicKey25519Proposition> outputPropositions = new ArrayList<>();
        ByteArrayOutputStream outputPropositionsValuesBytes = new ByteArrayOutputStream();
        for(Pair<PublicKey25519Proposition, Long> pair : outputs) {
            outputPropositions.add(pair.getKey());
            outputPropositionsValuesBytes.write(Longs.toByteArray(pair.getValue()), 0,8);
        }
        byte[] outputPropositionsBytes = _propositionSerializer.toBytes(outputPropositions);
        byte[] signaturesBytes = _signaturesSerializer.toBytes(_signatures);

        return Bytes.concat(                                        // minimum RegularTransaction length is 40 bytes
                Longs.toByteArray(fee()),                           // 8 bytes
                Longs.toByteArray(timestamp()),                     // 8 bytes
                Ints.toByteArray(inputBoxesBytes.length),           // 4 bytes
                inputBoxesBytes,                                    // depends on previous value (>=4 bytes)
                Ints.toByteArray(outputPropositionsBytes.length),   // 4 bytes
                outputPropositionsBytes,                            // depends on previous value (>=4 bytes)
                outputPropositionsValuesBytes.toByteArray(),        // depends on outputPropositions count (>=0 bytes)
                Ints.toByteArray(signaturesBytes.length),           // 4 bytes
                signaturesBytes                                     // depends on previous value (>=4 bytes)
        );
    }

    public static Try<RegularTransaction> parseBytes(byte[] bytes) {
        try {
            if(bytes.length < 40)
                throw new IllegalArgumentException("Input data corrupted.");
            if(bytes.length > MAX_TRANSACTION_SIZE)
                throw new IllegalArgumentException("Input data length is too large.");

            int offset = 0;

            long fee = BytesUtils.getLong(bytes, offset);
            offset += 8;

            long timestamp = BytesUtils.getLong(bytes, offset);
            offset += 8;

            int batchSize = BytesUtils.getInt(bytes, offset);
            offset += 4;
            List<RegularBox> inputs = _boxSerializer.parseBytes(Arrays.copyOfRange(bytes, offset, offset + batchSize)).get();
            offset += batchSize;

            batchSize = BytesUtils.getInt(bytes, offset);
            offset += 4;
            List<PublicKey25519Proposition> outputPropositions = _propositionSerializer.parseBytes(Arrays.copyOfRange(bytes, offset, offset + batchSize)).get();
            offset += batchSize;

            List<Pair<PublicKey25519Proposition, Long>> outputs =  new ArrayList<>();
            for(PublicKey25519Proposition proposition : outputPropositions) {
                outputs.add(new Pair<>(proposition, BytesUtils.getLong(bytes, offset)));
                offset += 8;
            }

            batchSize = BytesUtils.getInt(bytes, offset);
            offset += 4;
            if(bytes.length != offset + batchSize)
                throw new IllegalArgumentException("Input data corrupted.");
            List<Signature25519> signatures = _signaturesSerializer.parseBytes(Arrays.copyOfRange(bytes, offset, offset + batchSize)).get();

            return new Success<>(new RegularTransaction(inputs, outputs, signatures, fee, timestamp));
        } catch (Exception e) {
            return new Failure<>(e);
        }
    }

    public static RegularTransaction create(List<Pair<RegularBox, PrivateKey25519>> from,
                                                       List<Pair<PublicKey25519Proposition, Long>> to,
                                                       long fee,
                                                       long timestamp) {
        if(from == null || to == null)
            throw new IllegalArgumentException("Parameters can't be null.");
        if(from.size() > MAX_TRANSACTION_UNLOCKERS)
            throw new IllegalArgumentException("Transaction from count is too large.");
        if(to.size() > MAX_TRANSACTION_NEW_BOXES)
            throw new IllegalArgumentException("Transaction to count is too large.");

        List<RegularBox> inputs = new ArrayList<>();
        List<Signature25519> fakeSignatures = new ArrayList<>();
        for(Pair<RegularBox, PrivateKey25519> item : from) {
            inputs.add(item.getKey());
            fakeSignatures.add(null);
        }

        RegularTransaction unsignedTransaction = new RegularTransaction(inputs, to, fakeSignatures, fee, timestamp);

        byte[] messageToSign = unsignedTransaction.messageToSign();
        List<Signature25519> signatures = new ArrayList<>();
        for(Pair<RegularBox, PrivateKey25519> item : from) {
            signatures.add(item.getValue().sign(messageToSign));
        }

        RegularTransaction transaction = new RegularTransaction(inputs, to, signatures, fee, timestamp);
        if(!transaction.semanticValidity())
            throw new IllegalArgumentException("Created transaction is semantically invalid.");
        return transaction;
    }
}
