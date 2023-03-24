package io.horizen.transaction;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonView;
import io.horizen.utxo.box.Box;
import io.horizen.utxo.box.BoxUnlocker;
import io.horizen.proposition.Proposition;
import io.horizen.json.Views;
import io.horizen.transaction.exception.TransactionSemanticValidityException;
import io.horizen.transaction.mainchain.*;
import io.horizen.utils.*;
import io.horizen.utxo.transaction.BoxTransaction;
import io.horizen.utxo.transaction.MempoolIncompatibleTransactionIncompatibilityChecker;
import io.horizen.utxo.transaction.TransactionIncompatibilityChecker;
import scala.Array;
import sparkz.util.encode.Base16;
import java.util.*;
import static io.horizen.utxo.transaction.CoreTransactionsIdsEnum.MC2SCAggregatedTransactionId;

@JsonView(Views.Default.class)
@JsonIgnoreProperties({"mc2scTransactionsOutputs", "encoder"})
public final class MC2SCAggregatedTransaction
        extends BoxTransaction<Proposition, Box<Proposition>> {

    private byte[] mc2scTransactionsMerkleRootHash;
    private List<SidechainRelatedMainchainOutput> mc2scTransactionsOutputs;

    private List<Box<Proposition>> newBoxes;

    private final byte version;

    public final static byte MC2SC_AGGREGATED_TRANSACTION_VERSION = 1;
    public final static int MAX_MC2SC_AGGREGATED_TRANSACTION_SIZE = 2*1024*1024;

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

    @Override
    public TransactionIncompatibilityChecker incompatibilityChecker() {
        return MempoolIncompatibleTransactionIncompatibilityChecker.getChecker();
    }

    @Override
    public byte[] customDataMessageToSign() {
        return Array.emptyByteArray();
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
    public Boolean isCustom() { return false; }

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

        if (bytes().length > MAX_MC2SC_AGGREGATED_TRANSACTION_SIZE) {
            throw new TransactionSemanticValidityException("Transaction is too large: " + bytes().length);
        }
    }

    @Override
    public byte[] customFieldsData() {
        return Array.emptyByteArray();
    }
}
