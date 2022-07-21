package com.horizen.evm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.jna.Callback;
import com.sun.jna.Pointer;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;

final class LibEvmLogCallback implements Callback {
    private LibEvmLogCallback() {
        // this is a singleton, prevent more instances
    }

    // this singleton instance of the callback will be passed to libevm to be used for logging,
    // the static reference here will also prevent the callback instance from being garbage collected,
    // because without it the only reference might be from native code (libevm) and the JVM does not know about that
    static final LibEvmLogCallback instance = new LibEvmLogCallback();

    private static final ObjectMapper mapper = new ObjectMapper();
    private static final Logger logger = LogManager.getLogger(LibEvm.class);

    public void callback(Pointer message) {
        try {
            var json = message.getString(0);
            var data = mapper.readValue(json, HashMap.class);
            // parse and remove known properties from the map
            var level = glogToLog4jLevel((String) data.remove("lvl"));
            var file = data.remove("file");
            var line = data.remove("line");
            var fn = data.remove("fn");
            var msg = data.remove("msg");
            // ignore the timestamp supplied by go
            data.remove("t");
            // write to log4j logger
            logger.log(level, String.format("[%s:%s] (%s) %s %s", file, line, fn, msg, data));
        } catch (Exception e) {
            // note: make sure we do not throw any exception here because this callback is called by native code
            // for diagnostics we log the exception here, if it is caused by malformed json it will also include
            // the raw json string itself
            logger.warn("received invalid log message data from libevm", e);
        }
    }

    private static Level glogToLog4jLevel(String glogLevel) {
        switch (glogLevel) {
            case "trce":
                return Level.TRACE;
            default:
            case "dbug":
                return Level.DEBUG;
            case "info":
                return Level.INFO;
            case "warn":
                return Level.WARN;
            case "eror":
                return Level.ERROR;
            case "crit":
                return Level.FATAL;
        }
    }
}
