package com.horizen.transaction;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonView;
import com.google.common.primitives.Bytes;
import com.google.common.primitives.Ints;
import com.horizen.box.Box;
import com.horizen.box.BoxUnlocker;
import com.horizen.proposition.Proposition;
import com.horizen.serialization.Views;
import com.horizen.transaction.exception.TransactionSemanticValidityException;
import com.horizen.transaction.mainchain.*;
import com.horizen.utils.*;
import scorex.core.serialization.ScorexSerializer;
import scorex.util.encode.Base16;

import java.util.*;

import static com.horizen.transaction.CoreTransactionsIdsEnum.MC2SCAggregatedTransactionId;

@JsonView(Views.Default.class)
@JsonIgnoreProperties({"mc2scTransactionsOutputs", "encoder"})
public final class MC2SCAggregatedTransaction
        extends BoxTransaction<Proposition, Box<Proposition>> {

    private byte[] mc2scTransactionsMerkleRootHash;
    private List<SidechainRelatedMainchainOutput> mc2scTransactionsOutputs;

    private List<Box<Proposition>> newBoxes;

    private final byte version;

    public final static byte MC2SC_AGGREGATED_TRANSACTION_VERSION = 1;

    // Serializers definition
    private static ListSerializer<SidechainRelatedMainchainOutput> mc2scTransactionsSerializer = new ListSerializer<>(
            new DynamicTypedSerializer<>(
                new HashMap<Byte, ScorexSerializer<SidechainRelatedMainchainOutput>>() {{
                    put((byte)1, (ScorexSerializer)SidechainCreationSerializer.getSerializer());
                    put((byte)2, (ScorexSerializer)ForwardTransferSerializer.getSerializer());
                    put((byte)3, (ScorexSerializer)BwtRequestSerializer.getSerializer());
                }}, new HashMap<>()
            ));

    public MC2SCAggregatedTransaction(List<SidechainRelatedMainchainOutput> mc2scTransactionsOutputs, byte version) {
        if(mc2scTransactionsOutputs.isEmpty())
            throw new IllegalArgumentException("Empty sidechain related mainchain outputs passed.");
        this.mc2scTransactionsOutputs = mc2scTransactionsOutputs;
        this.version = version;
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
    public synchronized List<BoxUnlocker<Proposition>> unlockers() {
        return new ArrayList<>();
    }

    @Override
    public synchronized List<Box<Proposition>> newBoxes() {
        if (newBoxes == null) {
            newBoxes = new ArrayList<>();
            for(SidechainRelatedMainchainOutput t : mc2scTransactionsOutputs) {
                newBoxes.add((Box<Proposition>) t.getBox());
            }
        }
        return Collections.unmodifiableList(newBoxes);
    }

    @Override
    public long fee() {
        return 0;
    }

    @Override
    public byte transactionTypeId() {
        return MC2SCAggregatedTransactionId.id();
    }

    @Override
    public byte version() { return version; }

    @Override
    public String id() {
        return Base16.encode(mc2scMerkleRootHash());
    }

    @Override
    public byte[] messageToSign() {
        throw new UnsupportedOperationException("MC2SCAggregatedTransaction can not be signed.");
    }

    @JsonProperty("mc2scTransactionsMerkleRootHash")
    public synchronized byte[] mc2scMerkleRootHash() {
        if(mc2scTransactionsMerkleRootHash == null) {
            ArrayList<byte[]> hashes = new ArrayList<>();
            for (SidechainRelatedMainchainOutput t : mc2scTransactionsOutputs)
                hashes.add(t.hash());
            mc2scTransactionsMerkleRootHash = MerkleTree.createMerkleTree(hashes).rootHash();
        }
        return Arrays.copyOf(mc2scTransactionsMerkleRootHash, mc2scTransactionsMerkleRootHash.length);
    }

    public List<SidechainRelatedMainchainOutput> mc2scTransactionsOutputs() {
        return Collections.unmodifiableList(mc2scTransactionsOutputs);
    }

    public void semanticValidity() throws TransactionSemanticValidityException {
        if (version != MC2SC_AGGREGATED_TRANSACTION_VERSION) {
            throw new TransactionSemanticValidityException(String.format("Transaction [%s] is semantically invalid: " +
                    "unsupported version number.", id()));
        }
    }


    @Override
    public byte[] bytes() {
        byte[] transactions = mc2scTransactionsSerializer.toBytes(mc2scTransactionsOutputs);
        return Bytes.concat(                                        // minimum MC2SCAggregatedTransaction length is 13 bytes
                new byte[] {version},
                Ints.toByteArray(transactions.length),              // 4 bytes
                transactions                                        // depends on previous value (>=4 bytes)
        );
    }

    public static MC2SCAggregatedTransaction parseBytes(byte[] bytes) {
        if (bytes.length < 13)
            throw new IllegalArgumentException("Input data corrupted.");

        if (bytes.length > MAX_TRANSACTION_SIZE)
            throw new IllegalArgumentException("Input data length is too large.");

        int offset = 0;

        byte version = bytes[offset];
        offset += 1;

        if (version != MC2SC_AGGREGATED_TRANSACTION_VERSION) {
            throw new IllegalArgumentException(String.format("Unsupported transaction version[%d].", version));
        }

        int batchSize = BytesUtils.getInt(bytes, offset);
        offset += 4;
        List<SidechainRelatedMainchainOutput> mc2scTransactions = mc2scTransactionsSerializer.parseBytes(Arrays.copyOfRange(bytes, offset, offset + batchSize));

        return new MC2SCAggregatedTransaction(mc2scTransactions, version);
    }
}
