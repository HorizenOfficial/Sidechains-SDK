package com.horizen.examples;

import com.horizen.SidechainAppStopper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

class EvmAppStopper implements SidechainAppStopper {

    public Logger logger = LogManager.getLogger(EvmAppStopper.class);
    private DefaultApplicationState appState;
    private DefaultApplicationWallet appWallet;

    EvmAppStopper(DefaultApplicationState appState, DefaultApplicationWallet appWallet) { // pass storages themselves
        this.appState = appState;
        this.appWallet = appWallet;
    }

    @Override
     public void stopAll()  {
        logger.info("...stopping application...");
        appWallet.closeStorages();
        appState.closeStorages();
     }
}
