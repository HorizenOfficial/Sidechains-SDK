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
import com.horizen.utils.BytesUtils;
import com.horizen.utils.ListSerializer;
import com.horizen.utils.MerkleTree;
import com.horizen.utils.Utils;
import scala.util.Failure;
import scala.util.Success;
import scala.util.Try;
import scorex.core.serialization.Serializer;
import scorex.crypto.hash.Blake2b256;
import scorex.util.encode.Base16;

import java.util.*;

public final class MC2SCAggregatedTransaction extends BoxTransaction<Proposition, Box<Proposition>>
{
    private byte[] _mainchainBlockHash;
    private byte[] _mc2scTransactionsMerkleRootHash;
    private List<SidechainRelatedMainchainOutput> _mc2scTransactions;
    private long _timestamp;

    private List<BoxUnlocker<Proposition>> _unlockers;
    private List<Box<Proposition>> _newBoxes;

    // Serializers definition
    private static ListSerializer<SidechainRelatedMainchainOutput> _mc2scTransactionsSerializer = new ListSerializer<>(new HashMap<Integer, Serializer<SidechainRelatedMainchainOutput>>() {{
        put(1, (Serializer)ForwardTransferSerializer.getSerializer());
        put(2, (Serializer)CertifierLockSerializer.getSerializer());
    }});

    private MC2SCAggregatedTransaction(byte[] mainchainBlockHash, byte[] mc2scTransactionsMerkleRootHash, List<SidechainRelatedMainchainOutput> mc2scTransactions, long timestamp) {
        _mainchainBlockHash = Arrays.copyOf(mainchainBlockHash, mainchainBlockHash.length);
        _mc2scTransactionsMerkleRootHash = Arrays.copyOf(mc2scTransactionsMerkleRootHash, mc2scTransactionsMerkleRootHash.length);
        _mc2scTransactions = mc2scTransactions;
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
            for(SidechainRelatedMainchainOutput t : _mc2scTransactions)
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
        return 2;
    }

    // Note: maybe we also need to use _mainchainBlockHash for id calculation?
    @Override
    public String id() {
        return Base16.encode(Blake2b256.hash(_mc2scTransactionsMerkleRootHash));
    }

    @Override
    public byte[] messageToSign() {
        throw new UnsupportedOperationException("MC2SCAggregatedTransaction can not be signed.");
    }

    public byte[] mainchainBlockHash() {
        return Arrays.copyOf(_mainchainBlockHash, _mainchainBlockHash.length);
    }

    public byte[] mc2scMerkleRootHash() {
        return Arrays.copyOf(_mc2scTransactionsMerkleRootHash, _mc2scTransactionsMerkleRootHash.length);
    }

    public boolean semanticValidity() {
        // Transaction is valid if it contains all mc2sc transactions and merkle root based on them is equal to the one defined in constructor.
        if(_mc2scTransactions == null || _mc2scTransactions.size() == 0)
            return false;

        ArrayList<byte[]> hashes = new ArrayList<>();
        for(SidechainRelatedMainchainOutput t : _mc2scTransactions)
            hashes.add(t.hash());
        List<byte[]> merkleTree = MerkleTree.calculateMerkleTree(hashes);

        return Arrays.equals(_mc2scTransactionsMerkleRootHash, merkleTree.get(merkleTree.size() - 1));
    }


    @Override
    public byte[] bytes() {
        byte[] transactions = _mc2scTransactionsSerializer.toBytes(_mc2scTransactions);
        return Bytes.concat(                                        // minimum MC2SCAggregatedTransaction length is 80 bytes
                _mainchainBlockHash,                                // 32 bytes
                _mc2scTransactionsMerkleRootHash,                   // 32 bytes
                Longs.toByteArray(timestamp()),                     // 8 bytes
                Ints.toByteArray(transactions.length),              // 4 bytes
                transactions                                        // depends on previous value (>=4 bytes)
        );
    }

    public static Try<MC2SCAggregatedTransaction> parseBytes(byte[] bytes) {
        try {
            if(bytes.length < 80)
                throw new IllegalArgumentException("Input data corrupted.");
            if(bytes.length > MAX_TRANSACTION_SIZE)
                throw new IllegalArgumentException("Input data length is too large.");

            int offset = 0;

            byte[] mainchainBlockHash = Arrays.copyOfRange(bytes, offset, Utils.SHA256_LENGTH);
            offset += Utils.SHA256_LENGTH;

            byte[] merkleRoot = Arrays.copyOfRange(bytes, offset, Utils.SHA256_LENGTH);
            offset += Utils.SHA256_LENGTH;

            long timestamp = BytesUtils.getLong(bytes, offset);
            offset += 8;

            int batchSize = BytesUtils.getInt(bytes, offset);
            offset += 4;
            List<SidechainRelatedMainchainOutput> mc2scTransactions = _mc2scTransactionsSerializer.parseBytes(Arrays.copyOfRange(bytes, offset, offset + batchSize)).get();

            return new Success<>(new MC2SCAggregatedTransaction(mainchainBlockHash, merkleRoot, mc2scTransactions, timestamp));
        } catch (Exception e) {
            return new Failure<>(e);
        }
    }

    public static MC2SCAggregatedTransaction create(byte[] mainchainBlockHash, byte[] mc2scTransactionsMerkleRootHash, List<SidechainRelatedMainchainOutput> mc2scTransactions, long timestamp) {
        if(mainchainBlockHash == null || mc2scTransactionsMerkleRootHash == null || mc2scTransactions == null)
            throw new IllegalArgumentException("Parameters can't be null.");
        if(mainchainBlockHash.length != Utils.SHA256_LENGTH)
            throw new IllegalArgumentException(String.format("Mainchain block hash length is %d, expected to be %d", mainchainBlockHash.length, Utils.SHA256_LENGTH));
        if(mc2scTransactionsMerkleRootHash.length != Utils.SHA256_LENGTH)
            throw new IllegalArgumentException(String.format("Merkle root length is %d, expected to be %d", mc2scTransactionsMerkleRootHash.length, Utils.SHA256_LENGTH));
        if(mc2scTransactions.size() == 0)
            throw new IllegalArgumentException("MC2SC Transactions list is empty.");

        MC2SCAggregatedTransaction transaction = new MC2SCAggregatedTransaction(mainchainBlockHash, mc2scTransactionsMerkleRootHash, mc2scTransactions, timestamp);
        if(!transaction.semanticValidity())
            throw new IllegalArgumentException("Created transaction is semantically invalid. Proposed merkle root not equal to calculated one.");
        return transaction;
    }
}
