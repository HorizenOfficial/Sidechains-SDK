package com.horizen.evm;

import com.fasterxml.jackson.annotation.JsonValue;
import com.sun.jna.Callback;
import com.sun.jna.Pointer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.Map;

/**
 * Base class to be used when passing callbacks to libevm. Can and should be used when passing parameter objects via the
 * LibEvm.invoke() JSON interface. When a parameter derived from this type is passed to libevm it will be serialized as
 * an integer handle which maps to the instance of the callback. Callback handles need to be disposed when not in use
 * anymore, call close() - or better - use the try-with-resources syntax.
 */
abstract class LibEvmCallback implements AutoCloseable {
    // this singleton instance of the callback will be passed to libevm,
    // the static reference here will also prevent the callback instance from being garbage collected,
    // because without it the only reference might be from native code (libevm) and the JVM does not know about that
    final static CallbackProxy proxy = new CallbackProxy();

    private static final Logger logger = LogManager.getLogger();

    private static final Map<Integer, LibEvmCallback> callbacks = new HashMap<>();

    private static synchronized int register(LibEvmCallback callback) {
        // note: with N items in the map this will iterate for N+1 times, hence it should always find an unused handle
        for (int handle = 0; handle <= callbacks.size(); handle++) {
            if (!callbacks.containsKey(handle)) {
                callbacks.put(handle, callback);
                logger.trace("registered callback with handle {}: {}", handle, callback);
                return handle;
            }
        }
        throw new IllegalStateException("too many callback handles");
    }

    private static void unregister(int handle, LibEvmCallback callback) {
        if (!callbacks.remove(handle, callback)) {
            logger.warn("already unregistered callback with handle {}: {}", handle, callback);
            return;
        }
        logger.trace("unregistered callback with handle {}: {}", handle, callback);
    }

    private static String invoke(int handle, String args) {
        if (!callbacks.containsKey(handle)) {
            logger.warn("received callback with invalid handle: {}", handle);
            return null;
        }
        return callbacks.get(handle).invoke(args);
    }

    static class CallbackProxy implements Callback {
        public String callback(int handle, Pointer msg) {
            try {
                return invoke(handle, msg.getString(0));
            } catch (Exception e) {
                // note: make sure we do not throw any exception here because this callback is called by native code
                // for diagnostics we log the exception here, if it is caused by malformed json it will also include
                // the raw json string itself
                logger.warn("received invalid log message data from libevm", e);
            }
            return null;
        }
    }

    @JsonValue
    public final int handle;

    protected LibEvmCallback() {
        // acquire a callback handle on instantiation
        handle = register(this);
    }

    @Override
    public void close() {
        // release the callback handle on close
        unregister(handle, this);
    }

    public abstract String invoke(String args);
}
