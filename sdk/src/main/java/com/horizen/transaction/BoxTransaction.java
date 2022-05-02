package com.horizen.transaction;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonView;
import com.google.common.primitives.Bytes;
import com.google.common.primitives.Longs;
import com.horizen.box.Box;
import com.horizen.box.BoxUnlocker;
import com.horizen.proposition.Proposition;
import com.horizen.serialization.Views;
import com.horizen.transaction.exception.TransactionSemanticValidityException;
import com.horizen.utils.ByteArrayWrapper;
import com.horizen.utils.BytesUtils;
import scorex.crypto.hash.Blake2b256;

import java.io.ByteArrayOutputStream;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@JsonView(Views.Default.class)
@JsonIgnoreProperties({"signatures", "encoder", "customFieldsData", "customDataMessageToSign", "transactionTypeId"})
public abstract class BoxTransaction<P extends Proposition, B extends Box<P>> extends Transaction
{
    private HashSet<ByteArrayWrapper> _boxIdsToOpen;

    // TO DO: set real limits according to block size limits
    public final static int MAX_TRANSACTION_SIZE = 500000; // size in bytes
    public final static int MAX_TRANSACTION_UNLOCKERS = 1000;
    public final static int MAX_TRANSACTION_NEW_BOXES = 1000;

    @JsonProperty("unlockers")
    public abstract List<BoxUnlocker<P>> unlockers();

    @JsonProperty("newBoxes")
    public abstract List<B> newBoxes();

    @JsonProperty("fee")
    public abstract long fee();

    @Override
    public abstract byte transactionTypeId();

    @JsonProperty("version")
    @Override
    public abstract byte version();

    @Override
    public byte[] bytes() {
        return serializer().toBytes(this);
    }

    @JsonProperty("typeName")
    public String typeName() {
        return this.getClass().getSimpleName();
    }

    @JsonProperty("isCustom")
    public Boolean isCustom() { return true; } // All transactions presume customs until it not defined otherwise

    public abstract void semanticValidity() throws TransactionSemanticValidityException;

    // Transactions custom data to be considered during Transaction id calculation.
    // Note: in case custom field must be protected by the box unlocker proof,
    // it must be used in `customDataMessageToSign` only, so will be considered during id calculation.
    public abstract byte[] customFieldsData();

    // Transaction Id must depend on the whole transaction content including proof and custom data outside the Boxes.
    // Note: In future inside snarks id calculation will be different
    @JsonProperty("id")
    @Override
    public String id() {
        ByteArrayOutputStream proofsStream = new ByteArrayOutputStream();
        for(BoxUnlocker<P> u : unlockers()) {
            byte[] proofBytes = u.boxKey().bytes();
            proofsStream.write(proofBytes, 0, proofBytes.length);
        }

        return BytesUtils.toHexString(Blake2b256.hash(Bytes.concat(
                messageToSign(),
                proofsStream.toByteArray(),
                customFieldsData()
        )));
    }

    public synchronized final Set<ByteArrayWrapper> boxIdsToOpen() {
        if(_boxIdsToOpen == null) {
            _boxIdsToOpen = new HashSet<>();
            for (BoxUnlocker u : unlockers())
                _boxIdsToOpen.add(new ByteArrayWrapper(u.closedBoxId()));
        }
        return Collections.unmodifiableSet(_boxIdsToOpen);
    }

    public TransactionIncompatibilityChecker incompatibilityChecker() {
        return new DefaultTransactionIncompatibilityChecker();
    }

    // Transactions custom data hash to be included into messageToSign.
    // Note: there can be data which has no impact on message to sign, but only on id(). For example, custom non-box signature.
    public abstract byte[] customDataMessageToSign();

    @Override
    public byte[] messageToSign() {
        ByteArrayOutputStream unlockersStream = new ByteArrayOutputStream();
        for(BoxUnlocker<P> u : unlockers()) {
            byte[] boxId = u.closedBoxId();
            unlockersStream.write(boxId, 0, boxId.length);
        }

        ByteArrayOutputStream newBoxesStream = new ByteArrayOutputStream();
        for(B box : newBoxes()) {
            byte[] boxBytes = box.bytes();
            newBoxesStream.write(boxBytes, 0, boxBytes.length);
        }

        return Bytes.concat(
                new byte[]{version()},
                unlockersStream.toByteArray(),
                newBoxesStream.toByteArray(),
                Longs.toByteArray(fee()),
                customDataMessageToSign());
    }
}
