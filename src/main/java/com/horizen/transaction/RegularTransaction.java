package com.horizen.transaction;

import com.google.common.primitives.Bytes;
import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;
import com.horizen.box.BoxUnlocker;
import com.horizen.box.RegularBox;
import com.horizen.proof.Proof;
import com.horizen.proof.ProofOfKnowledge;
import com.horizen.proposition.PublicKey25519Proposition;
import com.horizen.secret.PrivateKey25519;
import com.horizen.secret.PrivateKey25519Companion;
import scorex.crypto.hash.Blake2b256;
import javafx.util.Pair;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;

public final class RegularTransaction extends NoncedBoxTransaction<PublicKey25519Proposition, RegularBox>
{
    private ArrayList<RegularBox> _inputs;
    private ArrayList<Pair<PublicKey25519Proposition, Long>> _outputs;
    // TO DO: change type to Signature25519 later.
    private ArrayList<ProofOfKnowledge> _signatures;
    private long _fee;
    private long _timestamp;
    private byte[] _hashWithoutNonce;

    public RegularTransaction(ArrayList<RegularBox> inputs,
                               ArrayList<Pair<PublicKey25519Proposition, Long>> outputs,
                               ArrayList<ProofOfKnowledge> signatures,
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
        return new RegularTransactionSerializer();
    }

    @Override
    public ArrayList<BoxUnlocker<PublicKey25519Proposition>> unlockers() {
        ArrayList<BoxUnlocker<PublicKey25519Proposition>> list = new ArrayList<>();
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
        return null;
    }

    @Override
    public ArrayList<RegularBox> newBoxes() {
        ArrayList<RegularBox> boxes = new ArrayList<>();
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

    public ArrayList<RegularBox> inputs() {
        return _inputs;
    }

    public ArrayList<Pair<PublicKey25519Proposition, Long>> outputs() {
        return _outputs;
    }
    // TO DO: change type to Signature25519 later.
    public ArrayList<ProofOfKnowledge> _signatures() {
        return _signatures;
    }

    private byte[] calculateHashWithoutNonceData() {
        ByteArrayOutputStream unlockersStream = new ByteArrayOutputStream();
        for(BoxUnlocker<PublicKey25519Proposition> u : unlockers())
            unlockersStream.write(u.closedBoxId(), 0, u.closedBoxId().length);

        ByteArrayOutputStream newBoxesStream = new ByteArrayOutputStream();
        for(RegularBox b : newBoxes())
            newBoxesStream.write(b.proposition().pubKeyBytes(), 0 , b.proposition().pubKeyBytes().length);


        return Bytes.concat(unlockersStream.toByteArray(),
                newBoxesStream.toByteArray(),
                Longs.toByteArray(_timestamp),
                Longs.toByteArray(_fee));
    }

    public static RegularTransaction create(ArrayList<Pair<RegularBox, PrivateKey25519>> from,
                                                       ArrayList<Pair<PublicKey25519Proposition, Long>> to,
                                                       long fee,
                                                       long timestamp) {
        ArrayList<RegularBox> inputs = new ArrayList<>();
        ArrayList<ProofOfKnowledge> fakeSignatures = new ArrayList<>();
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
        ArrayList<ProofOfKnowledge> signatures = new ArrayList<>();
        PrivateKey25519Companion companion = new PrivateKey25519Companion(); // TO DO: remove later.
        for(Pair<RegularBox, PrivateKey25519> item : from) {
            signatures.add(companion.sign(item.getValue(), messageToSign));
        }

        try {
            return new RegularTransaction(inputs, to, signatures, fee, timestamp);
        }
        catch (Exception e) {
            throw e;
        }
    }
}
