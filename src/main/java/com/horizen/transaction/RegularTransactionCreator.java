package com.horizen.transaction;

import com.horizen.box.Box;
import com.horizen.box.RegularBox;
import com.horizen.node.NodeWallet;
import com.horizen.proposition.PublicKey25519Proposition;
import com.horizen.secret.PrivateKey25519;
import com.horizen.secret.Secret;

import javafx.util.Pair;
import java.util.ArrayList;
import java.util.List;


public class RegularTransactionCreator {

    public static RegularTransaction create(NodeWallet wallet, List<Pair<PublicKey25519Proposition, Long>> to, PublicKey25519Proposition changeAddress, long fee, List<byte[]> boxIdsToExclude) {
        // TO DO:
        // 0. check parameters (fee >= 0, to.values >= 0, etc.)
        // 1. calculate sum of to.getValue(...) + fee
        // 2. get from wallet proper number of closed RegularBox ordered by creation time, which ids is not in boxIdsToExclude and sum of their values >= sum above
        // 3. set change to changeAddress if need
        // 4. construct inputs and outputs lists, timestamp
        // 5. try to do RegularTransaction.create(...)

        if(wallet == null || to == null || changeAddress == null || boxIdsToExclude == null)
            throw new IllegalArgumentException("Parameters can't be null.");
        long to_amount = 0;
        for(Pair<PublicKey25519Proposition, Long> pair : to) {
            if (pair.getValue() < 0)
                throw new IllegalArgumentException("Output values must be >= 0.");
            to_amount += pair.getValue();
        }

        if (fee < 0)
            throw new IllegalArgumentException("Fee must be >= 0.");
        to_amount += fee;


        List<RegularBox> boxes = new ArrayList<>();
        for(Box box : wallet.boxesOfType(RegularBox.class, boxIdsToExclude)) {
            boxes.add((RegularBox) box);
        }

        List<Pair<RegularBox, PrivateKey25519>> from = new ArrayList<>();
        long current_amount = 0;
        for(RegularBox box : boxes) {
            Secret s = wallet.secretByPublicKey(box.proposition()).get();
            if(s instanceof PrivateKey25519) {
                from.add(new Pair<>(box, (PrivateKey25519)s));
                current_amount += box.value();
                if (current_amount >= to_amount)
                    break;
            }
        }
        if(current_amount < to_amount)
            throw new IllegalArgumentException("Not enough balances in the wallet to create a transaction.");

        // add change to outputs
        if(current_amount > to_amount) {
            to.add(new Pair<>(changeAddress, current_amount - to_amount));
        }

        // TO DO: in HybridApp they use System.currentTimeMillis(). Is it a good solution?
        long timestamp = System.currentTimeMillis();
        return RegularTransaction.create(from, to, fee, timestamp);
    }
}
