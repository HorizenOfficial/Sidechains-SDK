package com.horizen.validation.crosschain.receiver;

import com.horizen.SidechainSettings;
import com.horizen.cryptolibprovider.Sc2scCircuit;
import com.horizen.storage.SidechainStateStorage;

public class CrossChainRedeemMessageValidator extends AbstractCrossChainRedeemMessageValidator {
    public CrossChainRedeemMessageValidator(SidechainSettings sidechainSettings, SidechainStateStorage scStateStorage, Sc2scCircuit sc2scCircuit) {
        super(sidechainSettings, scStateStorage, sc2scCircuit);
    }
}
