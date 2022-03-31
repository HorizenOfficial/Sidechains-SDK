package com.horizen.examples;

import com.horizen.SidechainAppStopper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

class SimpleAppStopper extends SidechainAppStopper {

    Logger logger = LogManager.getLogger(SimpleAppStopper.class);

    @Override
     public void stopAll()  {
        logger.info("...stopping application...");
     }
}