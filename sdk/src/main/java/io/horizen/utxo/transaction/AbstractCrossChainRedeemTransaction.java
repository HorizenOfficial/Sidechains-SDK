package io.horizen.utxo.transaction;


import io.horizen.proof.Signature25519;
import io.horizen.proposition.Proposition;
import io.horizen.utxo.box.Box;
import io.horizen.utxo.box.data.BoxData;
import io.horizen.utxo.box.data.CrossChainRedeemMessageBoxData;
import io.horizen.utxo.box.data.ZenBoxData;

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