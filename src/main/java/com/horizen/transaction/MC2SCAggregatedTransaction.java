package com.horizen.transaction;

import com.horizen.box.Box;
import com.horizen.box.BoxUnlocker;
import com.horizen.proposition.Proposition;
import scala.util.Try;
import scorex.core.serialization.Serializer;

import java.util.ArrayList;
import java.util.HashMap;

public final class MC2SCAggregatedTransaction extends BoxTransaction<Proposition, Box<Proposition>>
{

    @Override
    public MC2SCAggregatedTransactionSerializer serializer() {
        return new MC2SCAggregatedTransactionSerializer();
    }

    @Override
    public ArrayList<BoxUnlocker<Proposition>> unlockers() {
        // create array and put BoxUnlocker<ProofOfCoinBurnProposition> and/or BoxUnlocker<ProofOfBeingIncludedIntoCertificateProposition> inside
        return null;
    }

    @Override
    public ArrayList<Box<Proposition>> newBoxes() {
        // return list of RegularBoxes for MC2SC coins and CertifierRightBoxes for Certifier locks
        return null;
    }

    @Override
    public long fee() {
        return 0;
    }

    @Override
    public long timestamp() {
        return 0;
    }

    @Override
    public byte transactionTypeId() {
        return 2; // scorex.core.ModifierTypeId @@ 2.toByte
    }

    @Override
    public byte[] bytes() {
        return null;
    }

}
