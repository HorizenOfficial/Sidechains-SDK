package io.horizen.utils;

import io.horizen.account.state.AccountStateView;
import io.horizen.account.state.MessageProcessor;
import io.horizen.evm.StateDB;
import scala.collection.Seq;

/**
 * Useful in the bootstrapping phase, it returns a view with a null metadatastorage and overrides called methods which
 * would access it
 */
public class AccountStateViewForBootstrap extends AccountStateView {
    public AccountStateViewForBootstrap(StateDB stateDb, Seq<MessageProcessor> messageProcessors) {
        super(null, stateDb, messageProcessors);
    }

    @Override
    public int getConsensusEpochNumberAsInt() {
        return 0;
    }
}
