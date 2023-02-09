package com.horizen.evm;

import com.fasterxml.jackson.databind.type.TypeFactory;
import com.sun.jna.Callback;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

final class LibEvm {
    static native void Free(Pointer ptr);

    private static native void SetCallback(CallbackHandler callback);

    private static native void SetupLogging(int callbackHandle, String level);

    private static native JsonPointer Invoke(String method, JsonPointer args);

    private static final Logger logger = LogManager.getLogger();

    // this singleton instance of the callback will be passed to libevm to be used for logging,
    // the static reference here will also prevent the callback instance from being garbage collected,
    // because without it the only reference might be from native code (libevm) and the JVM does not know about that
    private static final CallbackHandler callbackInstance = new CallbackHandler();

    static String getOSLibExtension() {
        var os = System.getProperty("os.name").toLowerCase();
        if (os.contains("mac os")) {
            return "dylib";
        } else if (os.contains("windows")) {
            return "dll";
        }
        // default to linux file extension
        return "so";
    }

    static {
        var libName = "libevm." + getOSLibExtension();
        logger.info("loading library: {}", libName);
        // bind native methods in this class to libevm
        Native.register(libName);
        // register callback
        SetCallback(callbackInstance);
        // propagate log4j log level to glog
        SetupLogging(registerCallback(new GlogCallback(logger)), GlogCallback.log4jToGlogLevel(logger.getLevel()));
    }

    private LibEvm() {
        // prevent instantiation of this class
    }

    private static class InteropResult<R> {
        public String error;
        public R result;

        public boolean isError() {
            return !error.isEmpty();
        }

        @Override
        public String toString() {
            if (!error.isEmpty()) {
                return String.format("error: %s", error);
            }
            return "success";
        }
    }

    /**
     * Invoke function that has arguments and a return value.
     */
    static <R> R invoke(String method, JsonPointer args, Class<R> responseType) {
        var json = Invoke(method, args);
        // build type information to deserialize to generic type InteropResult<R>
        var type = TypeFactory.defaultInstance().constructParametricType(InteropResult.class, responseType);
        InteropResult<R> response = json.deserialize(type);
        if (response.isError()) {
            var message = String.format(
                "Error: \"%s\" occurred for method %s, with arguments %s",
                response.error,
                method,
                args == null ? null : args.toNative()
            );
            throw new InvokeException(message);
        }
        return response.result;
    }

    /**
     * Invoke function that has no arguments, but a return value.
     */
    static <R> R invoke(String method, Class<R> responseType) {
        return invoke(method, null, responseType);
    }

    /**
     * Invoke function that has arguments, but no return value.
     */
    static void invoke(String method, JsonPointer args) {
        invoke(method, args, Void.class);
    }

    /**
     * Invoke function that has no arguments and no return value.
     */
    static void invoke(String method) {
        invoke(method, null, Void.class);
    }

    interface LibEvmCallback {
        String callback(String args);
    }

    private static final int MAX_CALLBACKS = 10;
    private static final LibEvmCallback[] callbacks = new LibEvmCallback[MAX_CALLBACKS];

    static synchronized int registerCallback(LibEvmCallback callback) {
        for (int i = 0; i < callbacks.length; i++) {
            if (callbacks[i] == null) {
                callbacks[i] = callback;
                return i;
            }
        }
        throw new IllegalStateException("too many callback handles");
    }

    static void unregisterCallback(int handle) {
        callbacks[handle] = null;
    }

    private static class CallbackHandler implements Callback {
        public String callback(int handle, Pointer msg) {
            logger.info("received callback with handle {}", handle);
            try {
                // TODO: read block number from message, retrieve corresponding block hash and return as string
                if (callbacks[handle] == null) {
                    logger.warn("received callback with invalid handle: {}", handle);
                }
                var args = msg.getString(0);
                return callbacks[handle].callback(args);
            } catch (Exception e) {
                // note: make sure we do not throw any exception here because this callback is called by native code
                // for diagnostics we log the exception here, if it is caused by malformed json it will also include
                // the raw json string itself
                logger.warn("received invalid log message data from libevm", e);
            }
            return null;
        }
    }
}
