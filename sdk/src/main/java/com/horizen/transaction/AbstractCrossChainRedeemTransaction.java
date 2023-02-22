package com.horizen.transaction;

import com.horizen.box.Box;
import com.horizen.box.data.BoxData;
import com.horizen.box.data.CrossChainRedeemMessageBoxData;
import com.horizen.box.data.ZenBoxData;
import com.horizen.proof.Signature25519;
import com.horizen.proposition.Proposition;

import java.util.ArrayList;
import java.util.List;

abstract public class AbstractCrossChainRedeemTransaction extends AbstractRegularTransaction {
    protected final CrossChainRedeemMessageBoxData redeemMessageBox;

    public AbstractCrossChainRedeemTransaction(
            List<byte[]> inputZenBoxIds,
            List<Signature25519> inputZenBoxProofs,
            List<ZenBoxData> outputZenBoxesData,
            long fee,
            CrossChainRedeemMessageBoxData redeemMessageBox
    ) {
        super(inputZenBoxIds, inputZenBoxProofs, outputZenBoxesData, fee);
        this.redeemMessageBox = redeemMessageBox;
    }

    public CrossChainRedeemMessageBoxData getRedeemMessageBox() {
        return redeemMessageBox;
    }

    @Override
    protected List<BoxData<Proposition, Box<Proposition>>> getCustomOutputData() {
        List<BoxData<Proposition, Box<Proposition>>> result = new ArrayList<>();
        result.add((BoxData)redeemMessageBox);
        return result;
    }
}