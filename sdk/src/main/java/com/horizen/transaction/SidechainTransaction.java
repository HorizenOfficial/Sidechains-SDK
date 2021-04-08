package com.horizen.transaction;

import com.google.common.primitives.Bytes;
import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;
import com.horizen.box.CoinsBox;
import com.horizen.box.NoncedBox;
import com.horizen.box.BoxUnlocker;
import com.horizen.box.WithdrawalRequestBox;
import com.horizen.proposition.Proposition;
import com.horizen.utils.BytesUtils;
import com.horizen.utils.ZenCoinsUtils;
import scorex.crypto.hash.Blake2b256;

import java.io.ByteArrayOutputStream;
import java.util.List;

abstract public class SidechainTransaction<P extends Proposition, B extends NoncedBox<P>> extends BoxTransaction<P, B>
{
    // We don't need to calculate the hashWithoutNonce value each time, because transaction is immutable.
    private byte[] _hashWithoutNonce;

    private synchronized byte[] hashWithoutNonce() {
        if(_hashWithoutNonce == null) {
            ByteArrayOutputStream unlockersStream = new ByteArrayOutputStream();
            for (BoxUnlocker<P> u : unlockers()){
                unlockersStream.write(u.closedBoxId(), 0, u.closedBoxId().length);
            }

            ByteArrayOutputStream newBoxesPropositionsStream = new ByteArrayOutputStream();
            for (P proposition : newBoxesPropositions()){
                newBoxesPropositionsStream.write(proposition.bytes(), 0, proposition.bytes().length);
            }

            _hashWithoutNonce = Blake2b256.hash(
                    Bytes.concat(
                            unlockersStream.toByteArray(),
                            newBoxesPropositionsStream.toByteArray(),
                            Longs.toByteArray(timestamp()),
                            Longs.toByteArray(fee())
                    )
            );
        }
        return _hashWithoutNonce;
    }

    // Declaring the same rule for nonce calculation for all SidechainTransaction inheritors
    protected final long getNewBoxNonce(P newBoxProposition, int newBoxIndex) {
        byte[] hash = Blake2b256.hash(
                Bytes.concat(
                        newBoxProposition.bytes(),
                        hashWithoutNonce(),
                        Ints.toByteArray(newBoxIndex)
                )
        );
        return BytesUtils.getLong(hash, 0);
    }

    protected abstract List<P> newBoxesPropositions();

    public abstract boolean transactionSemanticValidity();

    // We check, that:
    // 1) there is no double spend boxes.
    // 2) nonces for new boxes were enforced according to our algorithm;
    // 3) coin balances are valid;
    // 4) non-coin boxes values are non-negative;
    // 5) fee is non-negative.
    // Then do inheritors check.
    @Override
    public final boolean semanticValidity() {
        // Check that transaction doesn't make a double spend,
        // by verifying that the the size of the unique set of box ids is equal to the unlockers size.
        if(unlockers().size() != boxIdsToOpen().size())
            return false;

        // Check output boxes nonce correctness and coin box values.
        long coinsCumulatedValue = 0;
        List<B> boxes = newBoxes();
        for(int i = 0; i < boxes.size(); i++) {
            B box = boxes.get(i);
            if(box.nonce() != getNewBoxNonce(box.proposition(), i)) {
                return false;
            }
            // check coins box value
            if(box instanceof CoinsBox || box instanceof WithdrawalRequestBox) {
                if(!ZenCoinsUtils.isValidMoneyRange(box.value()))
                    return false;
                coinsCumulatedValue += box.value();
                if(!ZenCoinsUtils.isValidMoneyRange(coinsCumulatedValue))
                    return false;
            } else {
                // Non-coins box should have at least non-negative value
                if (box.value() < 0)
                    return false;
            }
        }

        if(fee() < 0)
            return false;

        return transactionSemanticValidity();
    }
}