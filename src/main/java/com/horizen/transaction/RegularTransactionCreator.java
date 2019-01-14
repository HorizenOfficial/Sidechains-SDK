package com.horizen.transaction;

import com.horizen.SidechainWallet;
import com.horizen.proposition.PublicKey25519Proposition;
import javafx.util.Pair;

import java.util.ArrayList;

public class RegularTransactionCreator {
    // TO DO: replace SidechainWallet with its Java wrapper
    public static RegularTransaction create(SidechainWallet wallet, ArrayList<Pair<PublicKey25519Proposition, Long>> to, long fee, ArrayList<byte[]> boxIdsToExclude) {
        // TO DO:
        // 0. check parameters (fee >= 0, to.values >= 0, etc.)
        // 1. calculate sum of to.getValue(...) + fee
        // 2. get from wallet proper number of closed RegularBox, which ids is not in boxIdsToExclude and sum of their values >= sum above
        // 3. get wallet public key for regular charge
        // 4. construct inputs and outputs lists, timestamp
        // 5. try to do RegularTransaction.create(...)
        return null;
    }
}
