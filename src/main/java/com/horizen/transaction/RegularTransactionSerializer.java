package com.horizen.transaction;

import com.google.common.primitives.Bytes;
import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;
import com.horizen.box.RegularBox;
import com.horizen.box.RegularBoxSerializer;
import com.horizen.proof.ProofOfKnowledge;
import com.horizen.proposition.PublicKey25519Proposition;
import com.horizen.proposition.PublicKey25519PropositionSerializer;
import com.horizen.secret.PrivateKey25519;
import javafx.util.Pair;
import scala.util.Success;
import scala.util.Failure;
import scala.util.Try;
import scorex.core.serialization.Serializer;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

class RegularTransactionSerializer implements TransactionSerializer<RegularTransaction>
{
    private ListSerializer<RegularBox> _boxSerializer;
    private ListSerializer<PublicKey25519Proposition> _propositionSerializer;
    // TO DO: change to Signature25519
    private ListSerializer<ProofOfKnowledge> _signaturesSerializer;


    RegularTransactionSerializer() {
        HashMap<Integer, Serializer<RegularBox>> supportedBoxSerializers = new HashMap<>();
        supportedBoxSerializers.put(1, new RegularBoxSerializer());
        _boxSerializer  = new ListSerializer<>(supportedBoxSerializers);

        HashMap<Integer, Serializer<PublicKey25519Proposition>> supportedPropositionSerializers = new HashMap<>();
        supportedPropositionSerializers.put(1, PublicKey25519PropositionSerializer.getSerializer());
        _propositionSerializer = new ListSerializer<>(supportedPropositionSerializers);

        HashMap<Integer, Serializer<ProofOfKnowledge>> supportedProofSerializers = new HashMap<>();
        //supportedProofSerializers.put(1, new Signature25519Serializer());
        _signaturesSerializer = new ListSerializer<>(supportedProofSerializers);
    }

    @Override
    public byte[] toBytes(RegularTransaction obj) {
        byte[] inputBoxesBytes = _boxSerializer.toBytes(obj.inputs());

        ArrayList<Pair<PublicKey25519Proposition, Long>> outputs = obj.outputs();
        ArrayList<PublicKey25519Proposition> outputPropositions = new ArrayList<>();
        ByteArrayOutputStream outputPropositionsValuesBytes = new ByteArrayOutputStream();
        for(Pair<PublicKey25519Proposition, Long> pair : outputs) {
            outputPropositions.add(pair.getKey());
            outputPropositionsValuesBytes.write(Longs.toByteArray(pair.getValue()), 0,8);
        }
        byte[] outputPropositionsBytes = _propositionSerializer.toBytes(outputPropositions);
        byte[] signaturesBytes = _signaturesSerializer.toBytes(obj._signatures());

        return Bytes.concat(
                Longs.toByteArray(obj.fee()),                       // 8 bytes
                Longs.toByteArray(obj.timestamp()),                 // 8 bytes
                Ints.toByteArray(inputBoxesBytes.length),           // 4 bytes
                inputBoxesBytes,                                    // depends on previous value
                Ints.toByteArray(outputPropositionsBytes.length),   // 4 bytes
                outputPropositionsBytes,                            // depends on previous value
                outputPropositionsValuesBytes.toByteArray(),        // depends on outputPropositions count
                Ints.toByteArray(signaturesBytes.length),           // 4 bytes
                signaturesBytes                                     // depends on previous value
        );
    }

    @Override
    public Try<RegularTransaction> parseBytes(byte[] bytes) {
        try {
            int offset = 0;

            long fee = Longs.fromByteArray(Arrays.copyOfRange(bytes, offset, 8));
            offset += 8;

            long timestamp = Longs.fromByteArray(Arrays.copyOfRange(bytes, offset, 8));
            offset += 8;

            int batchSize = Ints.fromByteArray(Arrays.copyOfRange(bytes, offset, 4));
            ArrayList<RegularBox> inputs = _boxSerializer.parseBytes(Arrays.copyOfRange(bytes, offset, batchSize)).get();
            offset += batchSize;

            batchSize = Ints.fromByteArray(Arrays.copyOfRange(bytes, offset, 4));
            ArrayList<PublicKey25519Proposition> outputPropositions = _propositionSerializer.parseBytes(Arrays.copyOfRange(bytes, offset, batchSize)).get();
            offset += batchSize;

            ArrayList<Pair<PublicKey25519Proposition, Long>> outputs =  new ArrayList<>();
            for(PublicKey25519Proposition proposition : outputPropositions) {
                outputs.add(new Pair<>(proposition, Longs.fromByteArray(Arrays.copyOfRange(bytes, offset, 8))));
                offset += 8;
            }

            batchSize = Ints.fromByteArray(Arrays.copyOfRange(bytes, offset, 4));
            ArrayList<ProofOfKnowledge> signatures = _signaturesSerializer.parseBytes(Arrays.copyOfRange(bytes, offset, batchSize)).get();

            return new Success<>(new RegularTransaction(inputs, outputs, signatures, fee, timestamp));
        } catch (Exception e) {
            return new Failure(e);
        }
    }
}

