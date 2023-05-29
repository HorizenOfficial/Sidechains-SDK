package io.horizen.examples;

import io.horizen.SidechainAppStopper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

class SimpleAppStopper implements SidechainAppStopper {

    public Logger logger = LogManager.getLogger(SimpleAppStopper.class);
    private DefaultApplicationState appState;
    private DefaultApplicationWallet appWallet;

    SimpleAppStopper(DefaultApplicationState appState, DefaultApplicationWallet appWallet) { // pass storages themselves
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
