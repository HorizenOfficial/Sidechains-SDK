package com.horizen.evm;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;

class GlogCallback implements LibEvm.LibEvmCallback {
    private static final ObjectMapper mapper = new ObjectMapper();

    private final Logger logger;

    GlogCallback(Logger logger) {
        this.logger = logger;
    }

    @Override
    public String callback(String args) {
        try {
            var data = mapper.readValue(args, HashMap.class);
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
        return null;
    }

    public static Level glogToLog4jLevel(String glogLevel) {
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

    public static String log4jToGlogLevel(Level level) {
        switch (level.toString()) {
            default:
            case "ALL":
            case "TRACE":
                // glog does not have an ALL level, fallback to TRACE
                return "trce";
            case "DEBUG":
                return "dbug";
            case "INFO":
                return "info";
            case "WARN":
                return "warn";
            case "ERROR":
                return "eror";
            case "FATAL":
            case "OFF":
                // glog does not have an OFF level, fallback to FATAL
                return "crit";
        }
    }
}
