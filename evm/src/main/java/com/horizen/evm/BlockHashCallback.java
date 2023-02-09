package com.horizen.evm;

import com.horizen.evm.utils.Hash;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.math.BigInteger;

public abstract class BlockHashCallback extends LibEvmCallback {
    private static final Logger logger = LogManager.getLogger();

    protected abstract Hash getBlockHash(BigInteger blockNumber);

    @Override
    public String callback(String args) {
        logger.debug("received block hash callback");
        try {
            if (!args.startsWith("0x")) {
                logger.warn("received invalid block number: {}", args);
            } else {
                var blockNumber = new BigInteger(args.substring(2), 16);
                return getBlockHash(blockNumber).toString();
            }
        } catch (Exception e) {
            // note: make sure we do not throw any exception here because this callback is called by native code
            // for diagnostics we log the exception here, if it is caused by malformed json it will also include
            // the raw json string itself
            logger.warn("received invalid log message data from libevm", e);
        }
        return Hash.ZERO.toString();
    }
}
