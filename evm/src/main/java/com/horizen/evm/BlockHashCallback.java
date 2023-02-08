package com.horizen.evm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.horizen.evm.utils.Hash;
import com.sun.jna.Callback;
import com.sun.jna.Pointer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

class BlockHashCallback implements Callback {
    private static final ObjectMapper mapper = new ObjectMapper();

    private final Logger logger = LogManager.getLogger();

    public static int Acquire() {
        return 1;
    }

    public String callback(int handle, Pointer blockNumber) {
        logger.info("received block hash callback");
        try {
            // TODO: read block number from message, retrieve corresponding block hash and return as string
//            var json = message.getString(0);
//            var data = mapper.readValue(json, HashMap.class);
        } catch (Exception e) {
            // note: make sure we do not throw any exception here because this callback is called by native code
            // for diagnostics we log the exception here, if it is caused by malformed json it will also include
            // the raw json string itself
            logger.warn("received invalid log message data from libevm", e);
        }
        return Hash.ZERO.toString();
    }
}
