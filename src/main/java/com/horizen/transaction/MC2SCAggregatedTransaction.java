package com.horizen.transaction;

import com.google.common.primitives.Bytes;
import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;
import com.horizen.box.Box;
import com.horizen.box.BoxUnlocker;
import com.horizen.proposition.Proposition;
import com.horizen.transaction.mainchain.CertifierLockSerializer;
import com.horizen.transaction.mainchain.ForwardTransferSerializer;
import com.horizen.transaction.mainchain.SidechainRelatedMainchainOutput;
import com.horizen.utils.*;
import scala.util.Failure;
import scala.util.Success;
import scala.util.Try;
import scorex.core.serialization.Serializer;
import scorex.crypto.hash.Blake2b256;
import scorex.util.encode.Base16;

import java.util.*;

public final class MC2SCAggregatedTransaction extends BoxTransaction<Proposition, Box<Proposition>>
{
    public static final byte TRANSACTION_TYPE_ID = 2;
    private byte[] _mc2scTransactionsMerkleRootHash;
    private List<SidechainRelatedMainchainOutput> _mc2scTransactionsOutputs;
    private long _timestamp;

    private List<BoxUnlocker<Proposition>> _unlockers;
    private List<Box<Proposition>> _newBoxes;

    // Serializers definition
    private static ListSerializer<SidechainRelatedMainchainOutput> _mc2scTransactionsSerializer = new ListSerializer<>(
            new SerializableCompanion<>(
                new HashMap<Byte, Serializer<? extends SidechainRelatedMainchainOutput>>() {{
                    put((byte)1, (Serializer)ForwardTransferSerializer.getSerializer());
                    put((byte)2, (Serializer)CertifierLockSerializer.getSerializer());
                }}, new HashMap<>()
            ));

    private MC2SCAggregatedTransaction(byte[] mc2scTransactionsMerkleRootHash, List<SidechainRelatedMainchainOutput> mc2scTransactionsOutputs, long timestamp) {
        _mc2scTransactionsMerkleRootHash = Arrays.copyOf(mc2scTransactionsMerkleRootHash, mc2scTransactionsMerkleRootHash.length);
        _mc2scTransactionsOutputs = mc2scTransactionsOutputs;
        _timestamp = timestamp;
    }

    @Override
    public TransactionSerializer serializer() {
        return MC2SCAggregatedTransactionSerializer.getSerializer();
    }

    // no checker exists for current transaction type
    // keep check in mempool against this
    @Override
    public TransactionIncompatibilityChecker incompatibilityChecker() {
        return null;
    }

    @Override
    // TO DO: in SidechainState(BoxMinimalState) in validate(TX) method we need to introduce special processing for MC2SCAggregatedTransaction
    public synchronized List<BoxUnlocker<Proposition>> unlockers() {
        if(_unlockers == null) {
            _unlockers = new ArrayList<>();
            // TO DO: meybe in future we will spend WithdrawalRequestsBoxes for synced back Certificate.
        }
        return Collections.unmodifiableList(_unlockers);
    }

    @Override
    public List<Box<Proposition>> newBoxes() {
        if (_newBoxes == null) {
            _newBoxes = new ArrayList<>();
            for(SidechainRelatedMainchainOutput t : _mc2scTransactionsOutputs)
                _newBoxes.add(t.getBox());
        }
        return Collections.unmodifiableList(_newBoxes);
    }

    @Override
    public long fee() {
        return 0;
    }

    @Override
    public long timestamp() {
        return _timestamp;
    }

    @Override
    public byte transactionTypeId() {
        return TRANSACTION_TYPE_ID;
    }

    @Override
    public String id() {
        return Base16.encode(Blake2b256.hash(_mc2scTransactionsMerkleRootHash));
    }

    @Override
    public byte[] messageToSign() {
        throw new UnsupportedOperationException("MC2SCAggregatedTransaction can not be signed.");
    }

    public byte[] mc2scMerkleRootHash() {
        return Arrays.copyOf(_mc2scTransactionsMerkleRootHash, _mc2scTransactionsMerkleRootHash.length);
    }

    public boolean semanticValidity() {
        // Transaction is valid if it contains all mc2sc transactions and merkle root based on them is equal to the one defined in constructor.
        if(_mc2scTransactionsMerkleRootHash == null || _mc2scTransactionsMerkleRootHash.length != 32
                || _mc2scTransactionsOutputs == null || _mc2scTransactionsOutputs.size() == 0)
            return false;

        ArrayList<byte[]> hashes = new ArrayList<>();
        for(SidechainRelatedMainchainOutput t : _mc2scTransactionsOutputs)
            hashes.add(t.hash());
        byte[] merkleRootHash = MerkleTree.createMerkleTree(hashes).rootHash();

        return Arrays.equals(_mc2scTransactionsMerkleRootHash, merkleRootHash);
    }


    @Override
    public byte[] bytes() {
        byte[] transactions = _mc2scTransactionsSerializer.toBytes(_mc2scTransactionsOutputs);
        return Bytes.concat(                                        // minimum MC2SCAggregatedTransaction length is 48 bytes
                _mc2scTransactionsMerkleRootHash,                   // 32 bytes
                Longs.toByteArray(timestamp()),                     // 8 bytes
                Ints.toByteArray(transactions.length),              // 4 bytes
                transactions                                        // depends on previous value (>=4 bytes)
        );
    }

    public static Try<MC2SCAggregatedTransaction> parseBytes(byte[] bytes) {
        try {
            if(bytes.length < 48)
                throw new IllegalArgumentException("Input data corrupted.");
            if(bytes.length > MAX_TRANSACTION_SIZE)
                throw new IllegalArgumentException("Input data length is too large.");

            int offset = 0;

            byte[] merkleRoot = Arrays.copyOfRange(bytes, offset, Utils.SHA256_LENGTH);
            offset += Utils.SHA256_LENGTH;

            long timestamp = BytesUtils.getLong(bytes, offset);
            offset += 8;

            int batchSize = BytesUtils.getInt(bytes, offset);
            offset += 4;
            List<SidechainRelatedMainchainOutput> mc2scTransactions = _mc2scTransactionsSerializer.parseBytes(Arrays.copyOfRange(bytes, offset, offset + batchSize)).get();

            return new Success<>(new MC2SCAggregatedTransaction(merkleRoot, mc2scTransactions, timestamp));
        } catch (Exception e) {
            return new Failure<>(e);
        }
    }

    public static MC2SCAggregatedTransaction create(List<SidechainRelatedMainchainOutput> mc2scTransactionsOutputs, long timestamp) {
        if(mc2scTransactionsOutputs == null)
            throw new IllegalArgumentException("Parameters can't be null.");
        if(mc2scTransactionsOutputs.size() == 0)
            throw new IllegalArgumentException("MC2SC Transactions Outputs list is empty.");


        ArrayList<byte[]> hashes = new ArrayList<>();
        for(SidechainRelatedMainchainOutput t : mc2scTransactionsOutputs)
            hashes.add(t.hash());
        byte[] mc2scTransactionsMerkleRootHash = MerkleTree.createMerkleTree(hashes).rootHash();

        MC2SCAggregatedTransaction transaction = new MC2SCAggregatedTransaction(mc2scTransactionsMerkleRootHash, mc2scTransactionsOutputs, timestamp);
        if(!transaction.semanticValidity())
            throw new IllegalArgumentException("Created transaction is semantically invalid. Proposed merkle root not equal to calculated one.");
        return transaction;
    }
}
