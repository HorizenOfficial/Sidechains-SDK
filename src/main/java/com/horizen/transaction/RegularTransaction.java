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
    private byte[] _hashWithoutNonce;

    // Serialization
    private static ListSerializer<RegularBox> _boxSerializer;
    private static ListSerializer<PublicKey25519Proposition> _propositionSerializer;
    private static ListSerializer<Signature25519> _signaturesSerializer;

    static {
        HashMap<Integer, Serializer<RegularBox>> supportedBoxSerializers = new HashMap<>();
        supportedBoxSerializers.put(1, RegularBoxSerializer.getSerializer());
        _boxSerializer  = new ListSerializer<>(supportedBoxSerializers);

        HashMap<Integer, Serializer<PublicKey25519Proposition>> supportedPropositionSerializers = new HashMap<>();
        supportedPropositionSerializers.put(1, PublicKey25519PropositionSerializer.getSerializer());
        _propositionSerializer = new ListSerializer<>(supportedPropositionSerializers);

        HashMap<Integer, Serializer<Signature25519>> supportedProofSerializers = new HashMap<>();
        supportedProofSerializers.put(1, Signature25519Serializer.getSerializer());
        _signaturesSerializer = new ListSerializer<>(supportedProofSerializers);
    }

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
        _hashWithoutNonce = calculateHashWithoutNonceData();
    }

    @Override
    public TransactionSerializer serializer() {
        return RegularTransactionSerializer.getSerializer();
    }

    @Override
    public List<BoxUnlocker<PublicKey25519Proposition>> unlockers() {
        List<BoxUnlocker<PublicKey25519Proposition>> list = new ArrayList<>();
        for(int i = 0; i < _inputs.size() && i < _signatures.size(); i++) {
            int finalI = i;
            list.add(new BoxUnlocker<PublicKey25519Proposition>() {
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
        return list;
    }

    @Override
    public List<RegularBox> newBoxes() {
        List<RegularBox> boxes = new ArrayList<>();
        for(int i = 0; i < _outputs.size(); i++ ) {
            byte[] hash = Blake2b256.hash(Bytes.concat(_outputs.get(i).getKey().pubKeyBytes(), _hashWithoutNonce, Ints.toByteArray(i)));
            long nonce = Longs.fromByteArray(Arrays.copyOf(hash, 8));
            boxes.add(new RegularBox(_outputs.get(i).getKey(), nonce, _outputs.get(i).getValue()));
        }
        return boxes;
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

    private byte[] calculateHashWithoutNonceData() {
        ByteArrayOutputStream unlockersStream = new ByteArrayOutputStream();
        for(BoxUnlocker<PublicKey25519Proposition> u : unlockers())
            unlockersStream.write(u.closedBoxId(), 0, u.closedBoxId().length);

        ByteArrayOutputStream newBoxesStream = new ByteArrayOutputStream();
        for(Pair<PublicKey25519Proposition, Long> output : _outputs)
            newBoxesStream.write(output.getKey().pubKeyBytes(), 0 , output.getKey().pubKeyBytes().length);


        return Bytes.concat(unlockersStream.toByteArray(),
                newBoxesStream.toByteArray(),
                Longs.toByteArray(_timestamp),
                Longs.toByteArray(_fee));
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
            int offset = 0;

            long fee = Longs.fromByteArray(Arrays.copyOfRange(bytes, offset, offset + 8));
            offset += 8;

            long timestamp = Longs.fromByteArray(Arrays.copyOfRange(bytes, offset, offset + 8));
            offset += 8;

            int batchSize = Ints.fromByteArray(Arrays.copyOfRange(bytes, offset, offset + 4));
            offset += 4;
            List<RegularBox> inputs = _boxSerializer.parseBytes(Arrays.copyOfRange(bytes, offset, offset + batchSize)).get();
            offset += batchSize;

            batchSize = Ints.fromByteArray(Arrays.copyOfRange(bytes, offset, offset + 4));
            offset += 4;
            List<PublicKey25519Proposition> outputPropositions = _propositionSerializer.parseBytes(Arrays.copyOfRange(bytes, offset, offset + batchSize)).get();
            offset += batchSize;

            List<Pair<PublicKey25519Proposition, Long>> outputs =  new ArrayList<>();
            for(PublicKey25519Proposition proposition : outputPropositions) {
                outputs.add(new Pair<>(proposition, Longs.fromByteArray(Arrays.copyOfRange(bytes, offset, offset + 8))));
                offset += 8;
            }

            batchSize = Ints.fromByteArray(Arrays.copyOfRange(bytes, offset, offset + 4));
            offset += 4;
            List<Signature25519> signatures = _signaturesSerializer.parseBytes(Arrays.copyOfRange(bytes, offset, offset + batchSize)).get();

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
        List<RegularBox> inputs = new ArrayList<>();
        List<Signature25519> fakeSignatures = new ArrayList<>();
        for(Pair<RegularBox, PrivateKey25519> item : from) {
            inputs.add(item.getKey());
            fakeSignatures.add(null); // TO DO: replace with real Signature25519
        }

        RegularTransaction unsignedTransaction;
        try {
            unsignedTransaction = new RegularTransaction(inputs, to, fakeSignatures, fee, timestamp);
        }
        catch (Exception e) {
            throw e;
        }

        byte[] messageToSign = unsignedTransaction.messageToSign();
        List<Signature25519> signatures = new ArrayList<>();
        for(Pair<RegularBox, PrivateKey25519> item : from) {
            signatures.add(item.getValue().sign(messageToSign));
        }

        try {
            return new RegularTransaction(inputs, to, signatures, fee, timestamp);
        }
        catch (Exception e) {
            throw e;
        }
    }
}
