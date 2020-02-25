package com.horizen.transaction;

import com.google.inject.assistedinject.Assisted;
import com.horizen.box.NoncedBox;
import com.horizen.box.data.NoncedBoxData;
import com.horizen.proof.Proof;
import com.horizen.proposition.Proposition;

import java.util.List;

public interface SidechainCoreTransactionFactory {
    SidechainCoreTransaction create(@Assisted("inputIds") List<byte[]> inputsIds,
                                    @Assisted("outputsData") List<NoncedBoxData<Proposition, NoncedBox<Proposition>>> outputsData,
                                    @Assisted("proofs") List<Proof<Proposition>> proofs,
                                    @Assisted("fee") long fee,
                                    @Assisted("timestamp") long timestamp);
}
