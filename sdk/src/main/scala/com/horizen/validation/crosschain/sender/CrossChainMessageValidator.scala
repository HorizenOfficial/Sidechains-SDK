package com.horizen.validation.crosschain.sender

import com.horizen.SidechainState
import com.horizen.params.NetworkParams
import com.horizen.sc2sc.Sc2ScConfigurator
import com.horizen.storage.SidechainStateStorage

class CrossChainMessageValidator(
                                  networkParams: NetworkParams,
                                  sc2ScConfig: Sc2ScConfigurator,
                                  scState: SidechainState,
                                  scStateStorage: SidechainStateStorage
                                ) extends AbstractCrossChainMessageValidator(networkParams, sc2ScConfig, scState, scStateStorage){
}
