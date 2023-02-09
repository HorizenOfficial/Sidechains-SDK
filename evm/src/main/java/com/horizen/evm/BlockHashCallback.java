package com.horizen.evm;

import com.horizen.evm.utils.Hash;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.math.BigInteger;
import java.util.function.Function;

public class BlockHashCallback implements LibEvm.LibEvmCallback {
    private static final Logger logger = LogManager.getLogger();

    // TODO: refactor to abstract method?
    private final Function<BigInteger, Hash> getter;

    public BlockHashCallback(Function<BigInteger, Hash> getter) {
        this.getter = getter;
    }

    @Override
    public String callback(String args) {
        logger.info("received block hash callback");
        try {
            // TODO: read block number from message, retrieve corresponding block hash and return as string
            if (!args.startsWith("0x")) {
                logger.warn("received invalid block number: {}", args);
            } else {
                var blockNumber = new BigInteger(args.substring(2), 16);
                if (getter != null) {
                    return getter.apply(blockNumber).toString();
                }
                var bytes = blockNumber.toByteArray();
                var padded = new byte[Hash.LENGTH];
                System.arraycopy(bytes, 0, padded, padded.length - bytes.length, bytes.length);
                return new Hash(padded).toString();
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
