package com.horizen.transaction;

//import com.horizen.SidechainWallet;
import com.horizen.WalletBox;
import com.horizen.box.Box;
import com.horizen.box.RegularBox;
import com.horizen.proposition.ProofOfKnowledgeProposition;
import com.horizen.proposition.PublicKey25519Proposition;
import com.horizen.secret.PrivateKey25519;
import com.horizen.secret.Secret;

import javafx.util.Pair;
import scala.collection.Seq;
import java.util.ArrayList;
import java.util.List;

import scala.collection.JavaConverters.*;

public class RegularTransactionCreator {
    // TO DO: replace SidechainWallet with its Java wrapper
    public static RegularTransaction create(/*SidechainWallet wallet, */ArrayList<Pair<PublicKey25519Proposition, Long>> to, PublicKey25519Proposition changeAddress, long fee, ArrayList<byte[]> boxIdsToExclude) {
        // TO DO:
        // 0. check parameters (fee >= 0, to.values >= 0, etc.)
        // 1. calculate sum of to.getValue(...) + fee
        // 2. get from wallet proper number of closed RegularBox, which ids is not in boxIdsToExclude and sum of their values >= sum above
        // 3. set change to changeAddress if need
        // 4. construct inputs and outputs lists, timestamp
        // 5. try to do RegularTransaction.create(...)
        long to_amount = 0;
        for(Pair<PublicKey25519Proposition, Long> pair : to) {
            if (pair.getValue() < 0)
                throw new IllegalArgumentException("Output values must be >= 0.");
            to_amount += pair.getValue();
        }

        if (fee < 0)
            throw new IllegalArgumentException("Fee must be >= 0.");
        to_amount += fee;

        // TO DO: fill the list with real boxes from wallet, exclude "boxIdsToExclude" and filter by "age"
        ArrayList<RegularBox> boxes = new ArrayList<>();
        ArrayList<Pair<RegularBox, PrivateKey25519>> from = new ArrayList<>();
        long current_amount = 0;
        for(RegularBox box : boxes) {
            from.add(new Pair<>(box, null));//(PrivateKey25519)wallet.secret(box.proposition()).get())); // TO DO: get from java wallet wrapper, check type and for null, etc
            current_amount += box.value();
            if(current_amount >= to_amount)
                break;
        }
        if(current_amount < to_amount)
            throw new IllegalArgumentException("Not enough balances in the wallet to create a transction.");

        // add change to outputs
        if(current_amount > to_amount) {
            to.add(new Pair<>(changeAddress, current_amount - to_amount));
        }

        // TO DO: in HybridApp they use System.currentTimeMillis(). Is it a good solution?
        long timestamp = System.currentTimeMillis();
        return RegularTransaction.create(from, to, fee, timestamp);
    }
}
