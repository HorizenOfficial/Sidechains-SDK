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
import com.horizen.secret.PrivateKey25519Companion;
import com.horizen.utils.ListSerializer;
import com.horizen.utils.ParseBytesUtils;
import scala.util.Failure;
import scala.util.Success;
import scala.util.Try;
import scorex.core.serialization.Serializer;
import scorex.crypto.hash.Blake2b256;
import javafx.util.Pair;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

public final class RegularTransaction extends NoncedBoxTransaction<PublicKey25519Proposition, RegularBox>
{
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
    private static ListSerializer<RegularBox> _boxSerializer = new ListSerializer<>( new HashMap<Integer, Serializer<RegularBox>>() {{
            put(1, RegularBoxSerializer.getSerializer());
        }});
    private static ListSerializer<PublicKey25519Proposition> _propositionSerializer = new ListSerializer<>( new HashMap<Integer, Serializer<PublicKey25519Proposition>>() {{
            put(1, PublicKey25519PropositionSerializer.getSerializer());
        }});
    private static ListSerializer<Signature25519> _signaturesSerializer = new ListSerializer<>( new HashMap<Integer, Serializer<Signature25519>>() {{
            put(1, Signature25519Serializer.getSerializer());
        }});

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
    public List<BoxUnlocker<PublicKey25519Proposition>> unlockers() {
        if(_unlockers != null)
            return _unlockers;

        _unlockers = new ArrayList<>();
        for(int i = 0; i < _inputs.size() && i < _signatures.size(); i++) {
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
        return _unlockers;
    }

    @Override
    public List<RegularBox> newBoxes() {
        if(_newBoxes != null)
            return _newBoxes;

        _newBoxes = new ArrayList<>();
        for(int i = 0; i < _outputs.size(); i++ ) {
            byte[] hash = Blake2b256.hash(Bytes.concat(_outputs.get(i).getKey().pubKeyBytes(), hashWithoutNonce(), Ints.toByteArray(i)));
            long nonce = ParseBytesUtils.getLong(hash, 0);
            _newBoxes.add(new RegularBox(_outputs.get(i).getKey(), nonce, _outputs.get(i).getValue()));
        }
        return _newBoxes;
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
    public byte transactionTypeId() {
        return 1;
    }

    private byte[] hashWithoutNonce() {
        if(_hashWithoutNonce != null)
            return _hashWithoutNonce;

        ByteArrayOutputStream unlockersStream = new ByteArrayOutputStream();
        for(BoxUnlocker<PublicKey25519Proposition> u : unlockers())
            unlockersStream.write(u.closedBoxId(), 0, u.closedBoxId().length);

        ByteArrayOutputStream newBoxesStream = new ByteArrayOutputStream();
        for(Pair<PublicKey25519Proposition, Long> output : _outputs)
            newBoxesStream.write(output.getKey().pubKeyBytes(), 0 , output.getKey().pubKeyBytes().length);


        _hashWithoutNonce = Bytes.concat(unlockersStream.toByteArray(),
                        newBoxesStream.toByteArray(),
                        Longs.toByteArray(_timestamp),
                        Longs.toByteArray(_fee));

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

        return Bytes.concat(
                Longs.toByteArray(fee()),                           // 8 bytes
                Longs.toByteArray(timestamp()),                     // 8 bytes
                Ints.toByteArray(inputBoxesBytes.length),           // 4 bytes
                inputBoxesBytes,                                    // depends on previous value
                Ints.toByteArray(outputPropositionsBytes.length),   // 4 bytes
                outputPropositionsBytes,                            // depends on previous value
                outputPropositionsValuesBytes.toByteArray(),        // depends on outputPropositions count
                Ints.toByteArray(signaturesBytes.length),           // 4 bytes
                signaturesBytes                                     // depends on previous value
        );
    }

    public static Try<RegularTransaction> parseBytes(byte[] bytes) {
        try {
            if(bytes.length > MAX_TRANSACTION_SIZE)
                throw new IllegalArgumentException("Input data length is too large.");

            int offset = 0;

            if(bytes.length < 8)
                throw new IllegalArgumentException("Input data corrupted.");
            long fee = ParseBytesUtils.getLong(bytes, offset);
            offset += 8;

            if(bytes.length < offset + 8)
                throw new IllegalArgumentException("Input data corrupted.");
            long timestamp = ParseBytesUtils.getLong(bytes, offset);
            offset += 8;

            if(bytes.length < offset + 4)
                throw new IllegalArgumentException("Input data corrupted.");
            int batchSize = ParseBytesUtils.getInt(bytes, offset);
            offset += 4;

            byte[] inputsBytes = Arrays.copyOfRange(bytes, offset, offset + batchSize);
            if(_boxSerializer.parseListLength(inputsBytes).get() > MAX_TRANSACTION_UNLOCKERS)
                throw new IllegalArgumentException("Transaction inputs count is too large.");
            List<RegularBox> inputs = _boxSerializer.parseBytes(inputsBytes).get();
            offset += batchSize;

            batchSize = ParseBytesUtils.getInt(bytes, offset);
            offset += 4;

            byte[] outputBytes = Arrays.copyOfRange(bytes, offset, offset + batchSize);
            if(_propositionSerializer.parseListLength(outputBytes).get() > MAX_TRANSACTION_NEW_BOXES)
                throw new IllegalArgumentException("Transaction outputs count is too large.");
            List<PublicKey25519Proposition> outputPropositions = _propositionSerializer.parseBytes(outputBytes).get();
            offset += batchSize;

            List<Pair<PublicKey25519Proposition, Long>> outputs =  new ArrayList<>();
            for(PublicKey25519Proposition proposition : outputPropositions) {
                outputs.add(new Pair<>(proposition, ParseBytesUtils.getLong(bytes, offset)));
                offset += 8;
            }

            batchSize = ParseBytesUtils.getInt(bytes, offset);
            offset += 4;
            byte[] signaturesBytes = Arrays.copyOfRange(bytes, offset, offset + batchSize);
            if(_signaturesSerializer.parseListLength(signaturesBytes).get() > MAX_TRANSACTION_UNLOCKERS)
                throw new IllegalArgumentException("Transaction signatures count is too large.");
            if(bytes.length != offset + batchSize)
                throw new IllegalArgumentException("Input data corrupted.");
            List<Signature25519> signatures = _signaturesSerializer.parseBytes(signaturesBytes).get();

            return new Success<>(new RegularTransaction(inputs, outputs, signatures, fee, timestamp));
        } catch (Exception e) {
            return new Failure(e);
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
        PrivateKey25519Companion companion = PrivateKey25519Companion.getCompanion();
        for(Pair<RegularBox, PrivateKey25519> item : from) {
            signatures.add(companion.sign(item.getValue(), messageToSign));
        }

        return new RegularTransaction(inputs, to, signatures, fee, timestamp);
    }
}
